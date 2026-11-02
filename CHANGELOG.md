# Changelog

Each version's jar is named `tierprofiles-<version>+mc<minecraft>.jar`, for example
`tierprofiles-0.3.0+mc1.21.11.jar`.

## 0.6.0 (2 Nov 2026)

- Much faster lookups. Each list now has its own queue and works on several players at once, and each list's tiers show the moment they arrive. A 40-player lobby went from 20 to 40 seconds to about 2 seconds for MCTiers and 5 seconds for every list.
- Tiers are saved between sessions, for up to 7 days. After a server switch or a restart, players you've seen before show their tiers straight away while they're checked again.
- A list that hiccups is tried again after a moment, and only rested after failing three times running. PVPHQ, which often refuses busy moments, is asked again on its own a minute later.
- New "Best from any list", on by default: a tag shows the player's highest tier whichever list it's on. Turn it off in Tags to go back to your List order.
- Servers that fill the tab list with real players' names under made-up IDs, such as a top players board, now get tiers on those rows too.
- On the card, tier chips use a faint tint instead of a solid box, so they match the card's background and opacity.
- A list that hasn't answered says "Looking up...", and one that couldn't says "No answer", instead of "Unranked".

## 0.5.0 (2 Nov 2026)

- The TierProfiles server is online, at `https://tierprofiles.asepritez.workers.dev`.
- Other players' card styles load from it whichever way tiers are looked up, so you see the cards people picked.
- New "Card styles" switch, on the welcome page and in More, Advanced. Off keeps every card plain and sends nothing to our server for styles.
- The welcome page says what goes to the TierProfiles server.
- Settings that pointed at the old test address on this PC move to the live server.

## 0.4.0 (2 Nov 2026)

- A card opened from Tab shrinks to fit the space under the list, instead of running off the bottom of the screen.
- Card models show the skin's outer layer (hat, jacket, sleeves). Yours follows your Skin Customization options.
- Trim, Trim material and "Trims for" are in the Profile tab's main section, under the armor rows.
- Apply and Discard say what they did beside the buttons, such as "3 changes applied".

## 0.3.0 (2 Nov 2026)

Fixes from the first session on a real server.

- Settings always preview your own card, not the last player you pinned.
- On the card, name and title are centred together, and a name with no title is centred alone.
- Long titles stop before the moon instead of running under it.
- New Cards tab for how cards behave. Profile is now only your own card's look.
- Armor is chosen per piece: helmet, chestplate, leggings and boots each have their own row.
- "Browsing Tab shows": Tags only, Full profile or Nothing, with a preview when you hover each.
- "Tags when aiming" switch. The aim card no longer shows letter badges such as "MP".
- Reset all settings, under More. Nothing changes until Apply, so Discard undoes it.
- Tab key hints sit below the server's own text under the tab list.
- Search results fit the window.
- The welcome page is smaller, shows the game behind it, and Escape answers it, so it shows once.

## 0.2.0 (2 Nov 2026)

- Builds for Minecraft 1.21.11 as well as 26.3.
- The 1.21.11 build works with Fabric Loader 0.17.3 or newer.

## 0.1.0 (2 Nov 2026)

First version, for Minecraft 26.3.

- Lists tab: remove a whole tier list or any kit everywhere.
- Last column on the card: the date, a 4th tier, or nothing.
- All ranks key (Right Shift) shows every kit on every list.
- A card while browsing Tab, a "Stays open" timer, and hover help on every setting.
- Armor trims per piece, and hiding lists or kits on your own card.
- Looks tiers up straight from the five list websites, with no server needed.
