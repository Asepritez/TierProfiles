import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { CircuitBreaker } from "../src/breaker";
import { EdgeCache, ProfileCache } from "../src/cache";
import { Deps } from "../src/profiles";

export function fixture(name: string): string {
  return readFileSync(resolve(__dirname, "fixtures", name), "utf8");
}

/** The player the *-full fixtures describe. */
export const JOKING = { uuid: "1246a0ec-662b-45de-bb0b-d68cb1cce800", name: "JokingDK" };
export const ITZ = { uuid: "06ec3577-3299-45fa-bbdf-613b1f86c8ab", name: "ItzRealMe" };

/** Behaves like the Workers Cache API, and counts calls. */
export class FakeEdge implements EdgeCache {
  readonly stored = new Map<string, string>();
  matches = 0;
  puts = 0;

  async match(key: string): Promise<Response | undefined> {
    this.matches += 1;
    const body = this.stored.get(key);
    return body === undefined ? undefined : new Response(body);
  }

  async put(key: string, response: Response): Promise<void> {
    this.puts += 1;
    this.stored.set(key, await response.text());
  }
}

type Reply = { status: number; body: string } | Error;

export function reply(status: number, body = ""): Reply {
  return { status, body };
}

/** A fake `fetch`: `route` chooses a reply from the URL, and every call is logged. */
export function fakeFetch(route: (url: string) => Reply) {
  const calls: string[] = [];
  const fn = (async (input: RequestInfo | URL) => {
    const url = String(input);
    calls.push(url);
    const r = route(url);
    if (r instanceof Error) throw r;
    return new Response(r.body, { status: r.status });
  }) as typeof fetch;
  return { fn, calls };
}

/** Every list says "never heard of them". */
export const allNotFound = () => reply(404);

export function hostOf(url: string): string {
  return new URL(url).hostname;
}

export function makeDeps(
  fetcher: typeof fetch,
  options: { edge?: EdgeCache | null; clock?: { now: number } } = {},
): Deps & { deferred: Promise<unknown>[]; settle(): Promise<void> } {
  const clock = options.clock ?? { now: 1_800_000_000_000 };
  const deferred: Promise<unknown>[] = [];
  return {
    cache: new ProfileCache(options.edge ?? null),
    breaker: new CircuitBreaker(),
    fetch: fetcher,
    now: () => clock.now,
    defer: (work) => deferred.push(work),
    deferred,
    settle: async () => {
      await Promise.all(deferred);
    },
  };
}
