# 03: Networking

Minecraft does its drawing on one thread, 60 or more times a second. If that thread ever stops to wait
for the internet, the whole game freezes. So the rule in this mod is simple: **the render thread only
ever reads memory.**

## Two threads, one cache

`ProfileService` (in the core) has two sides:

- **The game's side**: `request(uuid, name)` adds a player to a queue, and `profile(uuid)` reads the
  cache. Both touch memory and return immediately.
- **The background side**: one thread wakes every 250 ms, takes up to 8 queued players, asks our server,
  and puts the answers in the cache.

The two sides share a `ConcurrentHashMap`, which is built for one thread writing while another reads.
The worst that can happen is that a tag shows up a moment later than it could have.

## Why tests can pretend to be the server

The service talks to a `Transport` interface instead of directly to the internet. The real one
(`HttpTransport`) uses Java's HTTP client. The tests use a `FakeServer` that can reply with anything or
pretend to be down. That's how the failure cases get tested for real, including a server that's down
for hours, garbage replies, and a queue of thousands.

Tests also pass in the clock (`() -> clock[0]`), so "10 minutes later" is one line rather than a
10-minute test.

## Being a good client

Things that keep us from hammering the server:

- **Batching.** Up to 8 players per request.
- **A cache.** A good answer stays fresh for 10 minutes.
- **Backoff.** If the server fails, we wait 5 seconds, then 10, 20 and so on, up to 5 minutes.
- **Limits.** The queue and cache have maximum sizes, so a huge server can't eat your memory.

Different failures retry at different speeds. `busy` means the server ran out of budget and will have
an answer soon, so we retry in 30 seconds. A list being down retries in 2 minutes. PVPHQ doesn't count,
since it's down all the time at the moment, and retrying would mean looking up every player constantly.

If a list goes down, we keep the last good answer for it instead of wiping it. Otherwise tags would
flicker off whenever a list had a bad minute. Note that a real change, such as a list now saying
"unranked", does replace the old answer. Only "we don't know" is ignored.

## Privacy

Three rules are enforced in code, not just promised:

1. **The tab list is the only source of names.** `LookupDriver` reads the list the server already shows
   everyone, so we can't reveal anyone hidden.
2. **Version 4 UUIDs only.** Real accounts have version 4 UUIDs. NPCs and fake tab entries usually
   don't, so we never send them.
3. **Nothing is sent before you've seen the notice.** `isAllowed` needs both the on switch and the
   first-run notice. There's a test that checks the exact JSON of a lookup contains a `uuid`, a `name`
   and nothing else.

Lookups aren't the only thing the mod sends. If you choose to save your card style, that goes to our
server too, and only then. Your Minecraft access token never comes to us: it only goes to Mojang. The
README lists everything that leaves your game.

## How the mod starts

`TierProfilesClient.onInitializeClient` is the first of our code the game runs. Fabric finds it through
`fabric.mod.json`. It starts the service, then registers a function to run on every game tick.

Registering for tick events is the common pattern in Fabric: you don't run your own loop, you ask the
game to call you. `LookupDriver.tick` counts to 20 (one second) and then checks the tab list.
