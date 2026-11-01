# 01: The server

The mod could call all five tier lists itself. It doesn't, for three reasons:

- **Politeness.** A hundred players on a server would mean five hundred requests from every client.
  A shared server asks once and remembers the answer for everybody.
- **Stability.** These APIs are unofficial and change without warning. If one changes, we fix the
  server once, and nobody has to update their mod.
- **One simple shape.** The mod only has to understand one format.

So the mod asks our server, and our server asks the lists.

## What a Cloudflare Worker is

A Worker is a small function that runs on Cloudflare's machines whenever a request arrives. There's
no server for us to keep running, and the free plan allows 100,000 requests a day. The whole thing
starts in `server/src/index.ts`, which looks at the path and decides what to do. That's called a
router.

The catch is the free plan's limits. Two of them shaped this code:

- **10 ms of CPU per request.** Waiting for a network reply doesn't count, only actual computing, so
  we can wait on slow lists but can't do heavy work.
- **50 subrequests per request.** A subrequest is any call the Worker makes to something else.

## Adapters: turning five formats into one

Each list gets a small file in `src/providers/`, and all of them have the same two jobs:

- `url(player)`: work out which address to call.
- `parse(status, body)`: turn the reply into our format.

Neither function touches the network. That's deliberate. Because `parse` only takes text and returns
data, the tests can feed it real saved replies from `test/fixtures/` with no internet at all. This
separation of "deciding" from "doing" is the most useful habit in this codebase.

MCTiers, PvPTiers and SubTiers turned out to share a format, so they share `rankings.ts`. McPvP and
PVPHQ have their own files.

The format they all become is defined in `src/types.ts`. Each list ends up in one of three states:

| Status | Meaning |
|---|---|
| `ok` | Here are their tiers |
| `unranked` | The list answered, and this player isn't on it |
| `unavailable` | We don't know, and `reason` says why |

The difference between the last two matters. "Unranked" is a fact. "Unavailable" admits we couldn't
find out, so the mod must not treat the two alike.

## Surprises from the real data

We captured real replies before writing the adapters, which paid off:

- **McPvP's search is loose.** Searching "ItzRealMe" returned 28 other people and not him. So we
  only trust a result whose UUID matches. A UUID is a permanent ID, while names can be shared or
  changed.
- **McPvP cuts results off at 50, sorted by points.** A low-ranked player with a common name could
  fall off the end. If the page is full and they aren't on it, we say `search_truncated` rather than
  wrongly calling them unranked.
- **`vanilla` on MCTiers is `crystal` on PvPTiers.** The attained timestamps match to the second.
  `kits.ts` gives each gamemode one name, so the mod can compare them across lists.
- **PVPHQ doesn't fail, it hangs.** An error would be easy. A request that never answers is worse,
  because the caller waits forever. So every call has a 3.5 second limit.

## The subrequest puzzle

The spec first said 8 players on 5 lists is 40 requests, under the limit of 50. But Cloudflare counts
cache reads and writes in that same 50. One cache entry per list would mean 40 reads, 40 fetches and
40 writes: 120.

The fix has two parts:

1. **One cache entry per player**, with a separate expiry for each list stored inside it. Now a full
   batch is 8 reads, 8 writes and up to 34 fetches.
2. **A budget.** `profiles.ts` counts down from 48. If it runs out, the remaining lists answer
   `busy` and aren't saved, so the next request fills them in. Lists are visited in priority order,
   so the unreliable PVPHQ is the one that goes without.

`getProfiles` works in four steps: read the cache, plan the calls, make them all at once, then build
the answer and save. Making calls at once matters: five lists at 300 ms each takes 300 ms, not 1.5
seconds.

## Remembering things

Caching means keeping an answer so you needn't ask again. Good answers last 30 minutes, "not ranked"
lasts 2 hours, and failures last 1 minute so we retry soon.

There are two layers in `cache.ts`:

- **Memory**: a plain `Map`. Instant and free, but gone whenever Cloudflare restarts the Worker.
- **The Cache API**: Cloudflare's own storage. Survives restarts.

Why both? Cloudflare only documents the Cache API as working on custom domains, and we may start on a
free `workers.dev` address. The memory layer means we still have some cache if that's a problem.

## The circuit breaker

Named after the switch in your fuse box. If PVPHQ fails twice in a row, `breaker.ts` stops calling it
for a minute and answers `circuit_open` straight away. Otherwise every lookup would wait the full 3.5
seconds on a list we already know is down. One bad list then costs nothing.

## How it was tested

- `npm test` runs 61 tests using saved replies from the real lists.
- `npx wrangler dev` runs the real Worker in Cloudflare's actual runtime on your own PC, at
  `http://127.0.0.1:8787`. We used this to check it against the live lists.

PVPHQ's adapter is the exception. Its test data is made up from another mod's field names, because
the real API has never answered us, and the code says so.

It has not been deployed. Things that could differ once it runs on Cloudflare: some sites treat
requests from Cloudflare's network differently, and the Cache API question above.
