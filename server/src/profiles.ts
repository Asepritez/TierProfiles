import { CircuitBreaker } from "./breaker";
import { CacheEntry, ProfileCache } from "./cache";
import { PROVIDERS } from "./providers";
import { Provider, ProviderError, USER_AGENT } from "./providers/provider";
import { ListId, ListResult, LIST_IDS, PlayerRef, Profile } from "./types";

const MINUTE = 60_000;
const OK_TTL = 30 * MINUTE;
const UNRANKED_TTL = 120 * MINUTE;
const FAILURE_TTL = MINUTE;

/** Free plan allows 50. Two are held back because a redirect also spends one. */
export const SUBREQUEST_BUDGET = 48;
export const MAX_BATCH = 8;
const LIST_TIMEOUT_MS = 3500;

export interface Deps {
  cache: ProfileCache;
  breaker: CircuitBreaker;
  fetch: typeof fetch;
  now(): number;
  /** Runs work after the response is sent. Workers would otherwise cancel it. */
  defer(work: Promise<unknown>): void;
  timeoutMs?: number;
}

/** How long to trust a result. Zero means don't store it. */
function ttlFor(result: ListResult): number {
  switch (result.status) {
    case "ok":
      return OK_TTL;
    case "unranked":
      return UNRANKED_TTL;
    case "unavailable":
      if (result.reason === "search_truncated") return UNRANKED_TTL;
      if (result.reason === "name_required" || result.reason === "name_too_short") return 0;
      // Checking the breaker or the budget is free, but a cache write is not.
      if (result.reason === "busy" || result.reason === "circuit_open") return 0;
      return FAILURE_TTL;
  }
}

async function callList(provider: Provider, player: PlayerRef, deps: Deps): Promise<ListResult> {
  const target = provider.url(player);
  if (typeof target !== "string") return target;

  try {
    const response = await deps.fetch(target, {
      headers: { "user-agent": USER_AGENT, accept: "application/json" },
      signal: AbortSignal.timeout(deps.timeoutMs ?? LIST_TIMEOUT_MS),
    });
    const body = await response.text();
    const result = provider.parse(response.status, body, player);
    deps.breaker.recordSuccess(provider.id);
    return result;
  } catch (error) {
    deps.breaker.recordFailure(provider.id, deps.now());
    if (error instanceof ProviderError) return { status: "unavailable", reason: error.reason };
    if (error instanceof Error && error.name === "TimeoutError") return { status: "unavailable", reason: "timeout" };
    return { status: "unavailable", reason: "network" };
  }
}

/**
 * Builds profiles for up to MAX_BATCH players. This runs in four steps so that
 * one slow or broken list can only ever cost its own column:
 *   1. read the cache entry for every player
 *   2. walk the lists in priority order, spending the subrequest budget
 *   3. wait for those calls, all at once
 *   4. assemble the answer, and store anything new after replying
 */
export async function getProfiles(players: PlayerRef[], deps: Deps): Promise<Profile[]> {
  const now = deps.now();

  // Each player may need one cache write, and those are paid for up front so
  // that fetching can't spend the last of the budget and leave results unsaved.
  let budget = SUBREQUEST_BUDGET - players.length;

  const entries = await Promise.all(
    players.map((player) => {
      const allowEdge = deps.cache.needsEdgeRead(player.uuid) && budget > 0;
      if (allowEdge) budget -= 1;
      return deps.cache.read(player.uuid, allowEdge);
    }),
  );

  const merged = players.map(() => new Map<ListId, ListResult>());
  const calls: Promise<void>[] = [];
  const changed = new Set<number>();
  const fresh: CacheEntry[] = entries.map((entry) => ({ ...entry }));

  for (const provider of PROVIDERS) {
    players.forEach((player, index) => {
      const cached = entries[index]?.[provider.id];
      if (cached && cached.expires > now) {
        merged[index]?.set(provider.id, cached.result);
        return;
      }

      const store = (result: ListResult): void => {
        merged[index]?.set(provider.id, result);
        const ttl = ttlFor(result);
        const entry = fresh[index];
        if (ttl > 0 && entry) {
          entry[provider.id] = { result, expires: deps.now() + ttl };
          changed.add(index);
        }
      };

      if (!deps.breaker.allows(provider.id, now)) {
        store({ status: "unavailable", reason: "circuit_open" });
      } else if (budget <= 0) {
        store({ status: "unavailable", reason: "busy" });
      } else {
        // A call that can't happen (say, McPvP with no name) costs nothing.
        if (typeof provider.url(player) === "string") budget -= 1;
        calls.push(callList(provider, player, deps).then(store));
      }
    });
  }

  await Promise.all(calls);

  for (const index of changed) {
    const player = players[index];
    const entry = fresh[index];
    if (player && entry) deps.defer(deps.cache.write(player.uuid, entry));
  }

  return players.map((player, index) => {
    const lists = {} as Record<ListId, ListResult>;
    for (const id of LIST_IDS) {
      lists[id] = merged[index]?.get(id) ?? { status: "unavailable", reason: "busy" };
    }
    return { uuid: player.uuid, name: player.name, lists };
  });
}
