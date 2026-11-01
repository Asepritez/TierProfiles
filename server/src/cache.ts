import type { ListId, ListResult } from "./types";

// Why one entry per player rather than one per player per list: on the free plan
// every cache.match and cache.put shares the same 50-per-request quota as fetch.
// Separate entries would cost 8 players x 5 lists x (match + fetch + put) = 120.
// One entry per player keeps a full batch to 8 matches, 8 puts and 34 fetches.

export interface CachedList {
  result: ListResult;
  /** Unix milliseconds. Each list keeps its own expiry inside the shared entry. */
  expires: number;
}

export type CacheEntry = Partial<Record<ListId, CachedList>>;

/** The part of the Workers Cache API we use, so tests can supply a fake. */
export interface EdgeCache {
  match(request: string): Promise<Response | undefined>;
  put(request: string, response: Response): Promise<void>;
}

/** Longest any list result lives, so the whole entry can expire after this. */
const ENTRY_MAX_AGE_SECONDS = 2 * 60 * 60;
const MEMORY_ENTRIES = 5000;

/**
 * A two-layer cache. Cloudflare only documents the Cache API as working on
 * custom domains, and we may run on workers.dev at first, so a plain Map in the
 * Worker's memory stands in front of it. The Map vanishes whenever Cloudflare
 * recycles the Worker, which is fine for a cache.
 */
export class ProfileCache {
  private readonly memory = new Map<string, CacheEntry>();

  constructor(private readonly edge: EdgeCache | null) {}

  private key(uuid: string): string {
    return `https://cache.tierprofiles.internal/v1/entry/${uuid}`;
  }

  /** True when reading this uuid would need a subrequest. */
  needsEdgeRead(uuid: string): boolean {
    return this.edge !== null && !this.memory.has(uuid);
  }

  async read(uuid: string, allowEdge: boolean): Promise<CacheEntry> {
    const held = this.memory.get(uuid);
    if (held) return held;
    if (!this.edge || !allowEdge) return {};

    try {
      const response = await this.edge.match(this.key(uuid));
      if (!response) return {};
      const entry = (await response.json()) as CacheEntry;
      this.remember(uuid, entry);
      return entry;
    } catch {
      return {};
    }
  }

  async write(uuid: string, entry: CacheEntry): Promise<void> {
    this.remember(uuid, entry);
    if (!this.edge) return;

    try {
      const response = new Response(JSON.stringify(entry), {
        headers: {
          "content-type": "application/json",
          "cache-control": `public, max-age=${ENTRY_MAX_AGE_SECONDS}`,
        },
      });
      await this.edge.put(this.key(uuid), response);
    } catch {
      // A failed cache write only means we ask the lists again next time.
    }
  }

  private remember(uuid: string, entry: CacheEntry): void {
    this.memory.delete(uuid); // re-inserting moves it to the newest position
    this.memory.set(uuid, entry);
    if (this.memory.size > MEMORY_ENTRIES) {
      const oldest = this.memory.keys().next().value;
      if (oldest !== undefined) this.memory.delete(oldest);
    }
  }
}
