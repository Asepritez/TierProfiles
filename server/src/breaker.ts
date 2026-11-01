import type { ListId } from "./types";

const FAILURES_TO_TRIP = 2;
const OPEN_MILLISECONDS = 60_000;

/**
 * A circuit breaker. PVPHQ doesn't fail fast: it hangs until we give up. Without
 * this, every lookup would wait out the timeout on a list we already know is
 * down. After two failures in a row we stop calling that list for a minute.
 *
 * State lives in Worker memory, so each Cloudflare instance learns on its own.
 * That's a small cost, and it needs no subrequests.
 */
export class CircuitBreaker {
  private readonly failures = new Map<ListId, number>();
  private readonly openUntil = new Map<ListId, number>();

  allows(list: ListId, now: number): boolean {
    return (this.openUntil.get(list) ?? 0) <= now;
  }

  recordSuccess(list: ListId): void {
    this.failures.delete(list);
    this.openUntil.delete(list);
  }

  recordFailure(list: ListId, now: number): void {
    const count = (this.failures.get(list) ?? 0) + 1;
    this.failures.set(list, count);
    if (count >= FAILURES_TO_TRIP) {
      this.openUntil.set(list, now + OPEN_MILLISECONDS);
      this.failures.delete(list);
    }
  }
}
