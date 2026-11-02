# 06: Settings, "The Inventory"

The settings screen is built like a Minecraft inventory:

- a **rail** of categories down the left, each with a real item as its icon
- an **options panel** where choices are picked like hotbar slots, with the same bright frame
- a **Live Stage** on the right, which is always visible and shows the result
- a **hotbar** of up to nine setups at the bottom, swapped with keys 1 to 9

## Immediate-mode UI

Most UI toolkits build a tree of widget objects: a button object, a slider object, and code that keeps
them in step with your data. This screen does something simpler. Every frame, it redraws everything and
asks questions as it goes:

```java
rule.bold = ui.toggle(x, y, rule.bold);
if (ui.button(x, y, 56, 20, "Apply", true)) {
    apply();
}
```

`ui.toggle` draws the switch, checks whether it was clicked this frame, and returns the new value. The
value lives in the settings themselves, so the screen can never show something different from what's
stored. This style is called **immediate mode**, and `Ui.java` is the whole toolkit.

## One catalog for everything

`SettingsCatalog` declares every setting once: its tab, its label, a sentence of help, whether it's
Advanced, and how to draw its control. Everything else comes from that list:

- each tab shows its essentials, with one Advanced row for the rest
- search looks through the same list, so it can't miss anything
- the header's "Search all 140 settings" is a real count

Adding a setting is one line, and it appears on its tab and in search with nothing else to wire up.

## Staged changes

Nothing you change does anything until Apply. The screen edits `ConfigEditor.staged()`, a private copy.
`changeCount()` compares the copy with what's live, value by value, which is where "3 changes staged"
comes from. The Live Stage draws from the staged copy, so you see changes before you commit them.

## Drawing at its own size

Minecraft's GUI scale changes how big everything is. At 1080p the usual "Auto" scale gives only
480 by 270 GUI pixels, and the design is laid out for 960 by 540. So the screen picks its own scale:
the largest whole number of real screen pixels per design pixel that fits. At 1080p that's exactly 2,
so the screen matches the design pixel for pixel, whatever your GUI scale.

Whole numbers matter. At 1.5 pixels per pixel, half the letters land between screen pixels and look
blurry. That's also why the card shrinks to 0.75 at GUI scale 4: 4 times 0.75 is exactly 3.

Mouse positions get divided by the same scale, so clicks land where you see things.

## A crash the tests found

On a fresh install, the privacy notice opens while the game is still loading. Creating an `ItemStack`
at that moment crashes Minecraft, because item data isn't ready yet. Every new player would have hit
this on their first launch. The in-game tests start from a fresh config, so they caught it, and now all
GUI item drawing goes through `Ui.item`, which waits for `itemsReady()`.
