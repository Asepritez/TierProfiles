# Layout grid

This is the contract for the profile card and the settings screen. The design drafts are built from
these numbers: `GRID` in `design/src/card.js` and `S` in `design/src/shell.js`. If you change a number
here, change it there too.

All numbers are **GUI pixels**. At GUI scale 2 on 1920×1080, 1 GUI pixel is 2 screen pixels.

## How the widths were measured

Measured from the game's own font (`assets/minecraft/textures/font/ascii.png`), as glyph width + 1 per
character, minus 1 for the last character. Space is 4. `design/src/make_font.py` does this.

| String | Width | Used for |
|---|---|---|
| `HT1` (widest of HT, MT, LT, ST 1 to 6) | 17 | Chip text |
| `McPvP beta` | 55 | List name |
| `PvPTiers`, `SubTiers` | 43 | List name |
| `28 May 2026` (widest full date) | 61 | Date |
| `Aug 2026` (widest month and year) | 45 | Full detail date |
| `Unranked` | 46 | Empty rows |

In the mod, don't hard-code these. Compute them once from `font.width()`, take the max with the table
above, and use the larger value. That keeps the layout safe with resource packs that change the font.
Do it for the widest possible string, not the current one, so columns never move between players.

## Profile card: Standard

Horizontal, left edge of the card = 0:

| Column | X | Width | Contents |
|---|---|---|---|
| Padding | 0 | 6 | |
| Model | 6 | 60 | 3D model, centred, full card height |
| Gap | 66 | 6 | |
| List | 72 | 76 | Logo 16, gap 4, name 56 at X 92 |
| Gap | 148 | 6 | |
| Kit 1 | 154 | 42 | Slot |
| Gap | 196 | 6 | |
| Kit 2 | 202 | 42 | Slot |
| Gap | 244 | 6 | |
| Kit 3 | 250 | 42 | Slot |
| Gap | 292 | 6 | |
| Date | 298 | 62 | **Right-aligned** to X 360 |
| Padding | 360 | 6 | |
| | **366** | | Card width |

Inside a 42-wide kit slot: 1 padding, icon 16, 1 gap, chip 24 with its text centred. The slot is one
filled shape, so it needs no separate icon tile.

Vertical, top of the card = 0:

| Band | Y | Height |
|---|---|---|
| Top strip | 0 | 32 |
| Gap | 32 | 4 |
| Rows | 36 | 19 each, 5 rows, content 18 tall in each |
| Padding | 131 | 6 |
| | **137** | Card height |

Every list always gets a row, in list order, MCTiers first. The card height never changes between
players.

### Top strip

| Item | X | Y | Notes |
|---|---|---|---|
| Name | 72 | 3 | 1.5× text, height 15 |
| Region chip | after name + 3 | 6 | Height 10 |
| Title | 72 | 18 | With badge if available, height 11 |
| Highest rank | 250 (Kit 3) | 3 | 1.5× text, gold |
| Rank's list | 250 (Kit 3) | 18 | `McPvP beta` where it applies |
| Total points | right edge 360 | 3 | 1.5× text, right-aligned |
| `points` | right edge 360 | 18 | Right-aligned |

The strip's right edge is the date column's right edge, X 360.

### Chip states

States live inside the slot and never change its size. They stack.

| State | Look |
|---|---|
| Normal | Filled `#151B21`, text in tier colour |
| Retired | No fill, 1px outline `#39424B`, text at 72% |
| Peak higher | Gold `#FFCF4A` stepped corner, 4×4 GUI pixels, top right of the slot |

No letters beside the chip. Peak and retired are spelled out in Full detail and in a tooltip.

### Rows with no tiers

Logo and name as normal. Muted `Unranked` at X 154, left-aligned, with colour `#8E98A2`. Kit 2, Kit 3
and Date stay empty. A list that is down reads the same.

### Other detail levels

| Level | Width | Height | Layout |
|---|---|---|---|
| Minimal | 366 | 61 | Strip, then one row. Up to 4 ranked lists at X 72 + 67n: logo 16, gap 3, slot 42. Model 49 tall. |
| Standard | 366 | 137 | As above |
| Full | 414 | 211 | Rows 31 tall: slot 18 and date 11 beneath. 5 slots 46 wide from X 154, step 52, right edge 408. Legend line at the bottom. Rank moves to right edge − 116. |

### Moon

The `full_moon` texture is 32×32 with an 8×8 body at X 12, Y 12, with a soft glow around it on black.
A plain 8×8 square at a large scale reads as a broken block, so:

- Draw the body as normal pixels, and drop its four corner pixels so it reads as a disc.
- Draw the glow as light, tinted `#96B2FF`, at about 5 times the texture's strength, and pulse it gently.
- Add the strip's shade first and the moon after it, so the shade doesn't dim the moon.
- On the card it's 3×, so 24 GUI pixels, centred at X 226, Y 16, in the strip between the name and the
  rank. In Full detail it's centred at X 268.
- Clip the glow to the strip, and draw the strip's text above it.
- If the name would reach it, hide the moon rather than overlap.

### When the card doesn't fit

Available width is the screen's GUI width minus 8. Pick the first that fits, and never overlap:

| Available | Layout | Width |
|---|---|---|
| 374 or more | Standard | 366 |
| 306 to 373 | No Date column | 298 |
| 258 to 305 | No Date, no Kit 3 | 250 |
| Under 258 | Minimal with no model, list slots from X 6 | One list per 67 |

In the top strip, the rank must not reach the points:

| Layout | Rank position |
|---|---|
| Standard | X 250, as above |
| No Date | Right edge − 100 |
| No Date, no Kit 3 | Hide the rank's list line. Right-align `#1` to right edge − 36. |
| Minimal with no model | Name and rank only, no title, no `points` label |

| Screen | GUI 2 | GUI 3 | GUI 4 |
|---|---|---|---|
| 854×480 | 427: Standard | 284: no Date, no Kit 3 | 213: Minimal |
| 1280×720 | 640: Standard | 426: Standard | 320: no Date |
| 1920×1080 | 960: Standard | 640: Standard | 480: Standard |

## Position

Nine anchors plus X and Y offsets. The default is top right. The margin from any screen edge is 10.
Keep the card clear of:

| Zone | Rule |
|---|---|
| Hotbar | 182 wide, centred, bottom 40 of the screen including hearts |
| Crosshair | A 33×33 box at the centre of the screen |
| Tab list | While Tab is held, a card that would touch the list drops its Date column. If it still touches, it moves below the list. |

A drag snaps to anchors and edges. If a position would hit a zone, slide the card clear and show the
final spot in the preview.

The card glides in from the nearest screen edge: left or right for corners and side anchors, top or
bottom for top-centre and bottom-centre, and the top for the centre anchor.

## Motion

| Step | Start | Length | Easing |
|---|---|---|---|
| Card glide in | 0 ms | 500 ms | Ease out, power 4 |
| Model settle | 220 ms | 340 ms | Ease out, 9 GUI px slide |
| Row *n* | 360 + 85*n* ms | 300 ms | Ease out, 13 GUI px slide and fade |
| Chip *m* in a row | Row start + 60 + 40*m* ms | 220 ms | Fade |
| Card glide out | On close | 300 ms | Ease in, power 3 |

All time-based. Speed multiplies the durations. Reduce motion skips all of it.

## Settings screen

Reference size 960×540. Screens are built from these regions, and every tab uses the same ones.

| Region | X | Y | Width | Height |
|---|---|---|---|---|
| Title | 10 | 10 | 88 | 24 |
| Search | 98 | 10 | 852 | 24 |
| Category rail | 10 | 40 | 88 | 456, tabs 32 tall on a 34 step |
| Options panel | 98 | 40 | 462 | 456 |
| Live Stage | 570 | 40 | 380 | 456 |
| Profiles hotbar | 98 | 506 | 184 | 24 |
| Apply and Discard | 570 | 506 | 380 | 24, right-aligned |

Inside the Live Stage, relative to its top left, with 7 padding:

| Item | Y | Height |
|---|---|---|
| Caption | 5 | 14 |
| World view | 22 | 264 |
| Tab row and chat line | 290 | 18 |
| Profile card (366 wide) | 312 | 137 |

The world view never shows card cosmetics. Armor only appears on the card.

Inside the options panel:

| Rule | Value |
|---|---|
| Padding | 10 |
| Row height | 28 for every row |
| Gap between rows | 4 |
| Label column | X 10, width 130, left-aligned |
| Control column | X 140, width 312, left-aligned |
| Slot | 20×20, no gap inside a slot row |
| Selected slot | 24×24 frame, as the vanilla hotbar does |
| Advanced row | Directly below the essentials, same row height |
| Help tooltip | Below the options, on the control column, never over a control |

Every control starts on the control column, so labels and controls form two straight edges. The Stage
needs 380 wide. Under 800 GUI wide, it becomes a toggle over the options panel.
