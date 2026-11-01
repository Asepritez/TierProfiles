import type { ListId, ListResult, PlayerRef } from "../types";

export const USER_AGENT = "TierProfiles/0.1 (+https://github.com/Asepritez/TierProfiles)";

/**
 * Thrown when a list misbehaves: bad status, unreadable body, unexpected shape.
 * Throwing counts as a failure against the circuit breaker, while simply
 * returning `unavailable` does not. A player with no name is not the list's
 * fault, so that case returns.
 */
export class ProviderError extends Error {
  constructor(readonly reason: string) {
    super(reason);
  }
}

/**
 * One tier list. `url` and `parse` are pure, so tests can feed saved responses
 * straight into `parse` with no network involved.
 */
export interface Provider {
  id: ListId;
  /** The URL to call, or a ready answer when the call can't be made at all. */
  url(player: PlayerRef): string | ListResult;
  parse(status: number, body: string, player: PlayerRef): ListResult;
}

export function parseJson(body: string): unknown {
  try {
    return JSON.parse(body);
  } catch {
    throw new ProviderError("bad_json");
  }
}

/** Status handling shared by every list: 404 means "never heard of them". */
export function checkStatus(status: number): ListResult | null {
  if (status === 404) return { status: "unranked" };
  if (status !== 200) throw new ProviderError(`http_${status}`);
  return null;
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function numberOrNull(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

export function stringOrNull(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}
