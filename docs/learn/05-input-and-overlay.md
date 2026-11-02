# 05: Input and overlay

The main promise of this mod: **looking someone up must never stop you moving.**

## Why not a Screen?

Minecraft's menus are `Screen`s. Opening one releases your movement keys, so you stop dead. In a fight
that can lose you the round. So our card is drawn on the **HUD**, the same layer as your hearts and
hotbar, and no Screen ever opens.

But then how do you read arrow keys? In a Screen they arrive as events. Outside one the game ignores
keys it doesn't know. So `KeyboardHandlerMixin` sits at the very start of the game's key handler and asks
`Controls.onKey` first. If we used the key, the game never sees it. If we didn't, it carries on as normal.

Only keys we actually use get swallowed. W, A, S and D pass straight through, and there's a test that
holds W with the card open and checks the player really moved.

## The tab list selection

`PlayerTabOverlayAccessor` reads the tab list's private player list, so our order matches the screen.
Accessors are a kind of mixin that exposes private things without changing behaviour.

The list fills column by column, so left and right jump by a whole column. `rowsPerColumn` is the same
sum the game uses for its own layout.

## Animation

Cards never just appear. All the animation logic lives in the core's `anim` package and works from
timestamps.

The important rule: **animation uses time, never frame count.** If you moved a card "2 pixels per frame",
it would run four times faster at 240 FPS than at 60. So everything is worked out from how much real time
has passed.

- **`Reveal`** is something that opens and closes. Its position comes from timestamps, and reversing part
  way carries on from the current position, so nothing jumps.
- **`Easing`** curves turn even progress into natural movement. `OUT_CUBIC` starts fast and settles
  gently, like something sliding to a stop.
- **`Stagger`** makes rows and chips start one after another, just as the card lands.
- **`Smoothed`** glides towards a moving target, which is how the tab highlight slides between rows. Each
  "half-life" closes half the gap. A test runs it at 30, 60 and 240 frames a second and checks they land
  in the same place.

The card keeps drawing while it leaves. So `CardState` separates *open* (it's staying up) from *visible*
(it's still on screen). Pressing Enter makes it not open immediately, then it glides out.

None of this blocks anything: animation is just a calculation made while drawing.

## The card model is a stand-in

`Portrait` is a pretend player that is never added to the world. The server doesn't know it exists, and
it has nothing to do with the real player's entity. This is what makes the "cosmetics are visual only"
rule true by construction: whatever we put on the model can't possibly touch anything real.

## Not revealing anyone

The game lets your crosshair pick invisible players, so `LookAtCard` checks `isInvisibleTo` and skips
them. Without that check, aiming at an invisible player would show their name on a card.

## How this was tested

`src/gametest/` holds real in-game tests. They start the actual game with an offline dev account and
press real keys. Two tricks make that possible:

- **Fake players with real UUIDs.** The dev account has an offline UUID, which lookups rightly skip, and a
  lone player gets no tab list. `TestWorld` adds tab entries and client-side bodies using real public
  UUIDs, so the tiers come from the real lists through our real server. None of this ships in the mod.
- **A frozen clock.** Animations read time through `CardState.now()`. Tests can replace that clock, so a
  screenshot shows exactly 100 ms into a glide rather than whenever the screenshot happened to land.

Run them with `./gradlew :26.3:runClientGameTest` or `./gradlew :1.21.11:runClientGameTest`. They need `wrangler dev` running in `server/`, and `node test/mock-mojang.mjs` for the card-saving test.
