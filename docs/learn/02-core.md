# 02: The core

The mod is split into two parts:

- `mod/core/`: plain Java. It knows about tiers, filters and settings, and nothing about Minecraft.
- `mod/src/`: the Minecraft-facing part, which draws what the core decides.

The split isn't just tidiness. Minecraft isn't on the core's classpath at all, so a Minecraft import
there won't compile. That means all this logic can be tested in seconds, with no game running.
`./gradlew :core:test` runs the core tests (355 on 2 Nov 2026).

Think of the core as the brain and the mod as the hands. The core says "show `[HT3]` in this colour",
and the mod works out how to actually paint that on a nametag.

## One scale for every tier

Lists disagree on how many tiers exist. MCTiers has High and Low on levels 1 to 5. McPvP adds Mid and
Sub, and goes down to level 6. To compare them, `Tier.java` gives every tier a score, where lower is
stronger:

| HT1 | MT1 | LT1 | ST1 | HT2 | MT2 | LT2 | ST2 | HT3 | ... |
|---|---|---|---|---|---|---|---|---|---|
| 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | ... |

The formula is `(level - 1) * 4 + band`. Now "only LT3 and above" is just "score of 10 or less", and
HT4 scores 12, so it's hidden. One small idea handles every comparison in the mod.

## The filter engine

`TagSelector` answers one question: which tags should appear on this player?

It walks the lists in your priority order. Each list offers its best tier that survives every filter
(retired, minimum tier, kit choice). The first offers fill the available slots. So with one slot,
MCTiers gets it if it has anything to offer, and McPvP only gets it if the lists above came up empty.
Since 0.7.0 PVPHQ comes first by default, then McPvP. (MCTiers led until it shut down in Nov 2026.) Before that, McPvP was never first unless you moved it, because it's new and
labelled beta.

One subtle detail: the unranked placeholder. If MCTiers says "unranked" but PVPHQ timed out, we don't
actually know the player is unranked, so we show nothing. The difference between "no" and "don't know"
runs through the whole project.

## Highlight rules

A rule says "if this player's tier is at or above X on list Y, style their name like this". Rules run
top to bottom and the first match wins, so order matters. Put "HT1 goes purple" above "HT3 goes red",
or the red rule catches HT1 players first.

Rules deliberately ignore your minimum-tier filters. Hiding a tag is a display choice, and shouldn't
stop a rule from noticing the tier exists.

## Tag formatting

The core can't draw, so `TagFormatter` produces a list of `Segment`s: "text `[` in grey, a sword icon,
text `HT3` in orange, text `]` in grey". The mod turns those into Minecraft text later. Keeping the
description separate from the drawing is why we can test `[HT3]` with no game running.

## Settings, and why they're in two pieces

- `Settings`: filters, styles, rules. Your named profiles hold these, and share codes carry them.
- `Global`: this PC's server URL, privacy notice, reduce motion. Never shared.

That split is a security decision. A share code is text from a stranger. Because a code can only hold
`Settings`, a malicious one can't redirect your mod to someone else's server. There's a test for
exactly that.

For the same reason, everything loaded from a file or code goes through `sanitize()`: numbers get
clamped, unknown values get dropped, and missing pieces get filled in. After that, the rest of the
code can trust every field.

## Staged Apply

`ConfigEditor` holds two copies: `live` (what the game uses) and `staged` (what the screen edits). Your
changes only touch `staged`. Apply makes it the new `live`. Discard throws it away.

Apply replaces the object rather than editing it in place. The game draws on one thread and settings
change on another, and swapping a single reference means the drawing code sees either the old config
or the new one, never a half-changed mix. Avoiding that kind of race by design is much easier than
fixing it later.

## Share codes

A code is your settings as JSON, compressed, encoded as text, with a checksum on the end:
`TP1:eJyr...4f3a9b21`. The checksum catches half-copied codes so we can say "copy the whole thing".

Compression has a danger: a tiny input can expand into gigabytes (a "zip bomb"). `ShareCode` stops
reading past 256 KB, and a test feeds it exactly that kind of attack.

## How we know the tests mean something

All the tests passed first time, which can mean the tests aren't really checking anything. So we broke
the code on purpose: flipping the tier comparison failed 31 tests, breaking highlight rules failed 9,
and removing the per-list minimum failed 2. This is called mutation testing, and it's a quick way to
find out whether a green test run can be trusted.
