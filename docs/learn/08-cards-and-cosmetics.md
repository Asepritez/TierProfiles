# 08: The card's layout, looks and position

## Columns that can't collide

The card's columns come from `docs/LAYOUT.md`, but those numbers are only minimums. `CardGrid` also
measures the font for the widest thing that could ever sit in each column (every tier label, every
month in a date, the longest list name) and uses whichever is larger. A resource pack with a wider font
widens the columns rather than causing overlap. `CardLayoutTest` checks this for every tier and every
date, so overlap isn't just unlikely, it's ruled out.

When the screen is too small, the card loses detail rather than squashing: first the date column, then
the third kit, then a one-line Minimal card. Before dropping detail, it tries a smaller size that stays
sharp. At GUI scale 4, a 0.75 card draws exactly like GUI scale 3. A test checks that at every common
screen size the card is sharp, readable and clear of the crosshair.

## Placement

`CardPlacement` turns your choice of anchor and offsets into a position, then keeps it on screen and
off the crosshair and hotbar, whatever you chose. Dragging in the Position tab's preview snaps to the
nearest anchor, and the leftover distance becomes the offset. The card glides in from whichever edge
its anchor is on.

## Backgrounds and effects

`CardStyle` draws the scenes: moon, starfield, nebula, embers and snow. They use a fixed set of
pseudo-random numbers, so each star stays in place between frames, and the clock, so they move at the
same speed at any frame rate. With reduce motion on, the clock stops and everything holds still.

The moon is the game's own texture, loaded at runtime. Its body is just 8 by 8 pixels, and a plain
square that size reads as a broken block, so the four corner pixels are left out to make a disc, with a
soft glow behind it.

## Armor that isn't real

The card's model is the `Portrait`, a stand-in that's never added to the world. `Wardrobe` dresses it
in real `ItemStack`s with real trim components, so it looks exactly like in-game armor. But it's only
ever on that stand-in, so it can't change anyone's real armor, can't reveal anything, and can't give
anyone an advantage. The Live Stage's world view never shows card armor at all.

## Real icons

With the Default icon style, kits on the card are real items drawn by the game's item renderer, so
tinted potions, shields and beds come out right. Classic uses the Programmer Art pack's older textures
through the in-memory icon pack, and falls back to the current art for items that pack doesn't have.

## Logos and WebP

List logos and title badges are downloaded at runtime and never bundled. SubTiers and the MCTiers
badges only publish WebP images, which Java can't read, so the mod bundles TwelveMonkeys' small
pure-Java WebP reader (BSD licensed). Only the decoder is bundled, never any image.

## Real data surprises

PVPHQ came back online during development, so its adapter could finally be checked against real
replies. The field names were right, but two details only real data showed: `globalPosition` uses
99999 to mean "no position", which would have shown a lone PVPHQ player as #99999, and its ladders have
names like `SPEAR_MACE` that needed mapping to our kit ids.
