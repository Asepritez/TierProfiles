# TierProfiles GUI drafts

## New in this update (in the real game)

### While in Tab: small card
Move through players with Tab held and the small card shows straight away.
![Small card while in Tab](ingame/u2-tp-02b-tab-small-card.png)

### While in Tab: big card
Set "While in Tab" to Big card and the full card swaps as you move. Enter keeps it.
![Big card while in Tab](ingame/u2-tp-02c-tab-big-card.png)

### Last column: 4th tier
![Four tiers instead of a date](ingame/u2-tp-last-fourth_tier.png)

### All ranks (Right Shift)
Every kit on every list, wrapping onto more lines. Here PVPHQ and SMP are hidden on the Lists tab.
![All ranks](ingame/u2-tp-all-ranks.png)

At GUI scale 4 it drops to a sharp smaller size instead of leaving kits out:
![All ranks at GUI 4](ingame/u2-tp-all-ranks-gui4.png)

### Lists tab
Click a list or a kit to remove it everywhere.
![Lists tab](ingame/u2-tp-config-lists.png)

### Profile: trims per piece, kits on my card
![Profile advanced](ingame/u2-tp-config-profile-advanced.png)

### Hover help
![Tooltip on a setting](ingame/u2-tp-config-tooltip-setting.png)
![Tooltip on a tab](ingame/u2-tp-config-tooltip-rail.png)

## In the real game

Screenshots from the actual mod running in Minecraft 26.3 with an offline test account. Every image
is in `ingame/`.

### Profile card
![Card in game](ingame/0021_tp-size-1920x1080-gui3.png)

### Settings: Tags
![Tags tab in game](ingame/0035_tp-config-tags.png)

### Settings: Profile, styled card
![Profile tab in game](ingame/0044_tp-config-profile-styled.png)

### Tab navigation
![Tab navigation in game](ingame/0004_tp-04-card-with-tab.png)

### Nametags
![Nametags in game](ingame/0001_tp-01-nametags.png)

## Revision 3

### Default card

Rebuilt on one fixed grid, in the game font. Dates and points share one right edge. A hollow chip means retired, and a gold corner means their peak was higher.

![Default card](shots/v3-01-default-card.png)

### Grid proof

The same card with debug guides on, proving every column lines up. The guides are only in this image. The numbers are in docs/LAYOUT.md.

![Grid proof](shots/v3-02-grid-proof.png)

### Sparse profile

Marlowww. Every list keeps its row, and a list with no result reads Unranked.

![Sparse profile](shots/v3-03-sparse-profile.png)

### Card detail

Minimal, Standard and Full on the same columns, and how to read a chip.

![Card detail](shots/v3-04-card-detail.png)

### Icons and logos

Real game items and official logos, used on every screen. The comparison shows default textures match McPvP exactly.

![Icons and logos](shots/v3-05-icons-logos.png)

### Card entrance, animated

The card glides in from its nearest edge, rows stagger in, then it glides out.

![Card entrance, animated](shots/v3-15-card-entrance.png)

### Position: top left

Glides in from the left. Beside the tab list it drops its date column, so nothing touches.

![Position: top left](shots/v3-14-position-tl.png)

### Position: top right

The default spot. Glides in from the right.

![Position: top right](shots/v3-14-position-tr.png)

### Position: bottom left

Clear of the hotbar and crosshair.

![Position: bottom left](shots/v3-14-position-bl.png)

### Position: bottom right

Clear of the hotbar and crosshair.

![Position: bottom right](shots/v3-14-position-br.png)

### Tab navigation

Hold Tab and use the arrow keys. Your movement keys keep working.

![Tab navigation](shots/v3-06-tab-navigation.png)

### Tab pinned card

Press Enter. The card sits beside the list without touching it.

![Tab pinned card](shots/v3-07-tab-pinned-card.png)

### Filter: before

Everyone showing HT4 tells you nothing. Lobby names and tiers are invented samples.

![Filter: before](shots/v3-08-filter-before.png)

### Filter: after

Only LT3 and above, so the players worth noticing stand out.

![Filter: after](shots/v3-09-filter-after.png)

### Name highlight rules

HT1 is purple and bold with a star, and above HT3 is red, in nametag, tab and chat.

![Name highlight rules](shots/v3-13-highlight-rules.png)

### Card styles: backgrounds

Moon, starfield, nebula, embers, snow and plain, with the new logos and card. The moon is the game's own texture.

![Card styles: backgrounds](shots/v3-10-styles-backgrounds.png)

### Card styles: borders and effects

Thin, double, glowing, shimmer and corner ornaments, then sparkles and motes, which stay in the model column.

![Card styles: borders and effects](shots/v3-11-styles-borders-effects.png)

### Moon card, animated

An 8 second loop: twinkling stars, a breathing moon glow, sparkles and shimmer.

![Moon card, animated](shots/v3-16-moon-card-animated.png)

### Moon card in the game

The styled card on the HUD, with vanilla armor and a trim on the model.

![Moon card in the game](shots/v3-12-moon-card-on-hud.png)

### The Inventory: Tags

The signature config. It works like a Minecraft inventory: category tabs with real items, options as slots with the hotbar selection frame, help as a real tooltip, and your setups in a hotbar you swap with 1 to 9. The Live Stage always shows nametag, tab row, chat and card.

![The Inventory: Tags](shots/v3-20-config-tags.png)

### The Inventory: Colours

Presets, tier, RGB and hex, with every surface updating on the Stage.

![The Inventory: Colours](shots/v3-21-config-colours.png)

### The Inventory: Rules

Ordered rules with the editor below them. First match wins.

![The Inventory: Rules](shots/v3-22-config-rules.png)

### The Inventory: Profile

Six essentials, including card detail, animation style and speed. Armor stays on the card, never on the world model.

![The Inventory: Profile](shots/v3-23-config-profile.png)

### The Inventory: Profile, Advanced

Everything else, with real armor, trims and materials as slots.

![The Inventory: Profile, Advanced](shots/v3-24-config-advanced.png)

### The Inventory: Position

Nine-anchor picker, X and Y offsets, and drag on a live screen. Red zones are the hotbar and crosshair, and yellow is the tab list.

![The Inventory: Position](shots/v3-25-config-position.png)

### The Inventory: Sharing

Name a setup, pick its hotbar icon, and share or import a code.

![The Inventory: Sharing](shots/v3-26-config-sharing.png)

### The Inventory: Keys

Every key, rebindable.

![The Inventory: Keys](shots/v3-27-config-keys.png)

### The Inventory: More

Lookups, reduce motion and icon style. Advanced holds Diagnostics, the only place a list can read as down.

![The Inventory: More](shots/v3-28-config-more.png)

### The Inventory: search

Search finds every setting, including those inside Advanced, with a live control on each result.

![The Inventory: search](shots/v3-29-config-search.png)

### First-run notice

Exactly what leaves your game, with the off switch.

![First-run notice](shots/v3-30-first-run.png)

### Draft against the real game

The current build beside the draft, one to one, with what matches and what still differs.

![Draft against the real game](shots/v3-31-game-compare.png)

## Older drafts

### Revision 2

#### New default card

Esports strip over quiet rows, with official logos and real items.

![New default card](shots/v2-01-default-card.png)

#### Default card, sparse

Marlowww, ranked only on MCTiers.

![Default card, sparse](shots/v2-02-default-card-sparse.png)

#### Card entrance

Animated: glide in, rows stagger, glide out.

![Card entrance](shots/v2-03-card-entrance.png)

#### Card detail

Minimal, Standard and Full.

![Card detail](shots/v2-04-card-detail.png)

#### Icons and logos

Real items, official logos, and the texture comparison with McPvP.

![Icons and logos](shots/v2-05-icons-logos.png)

#### Tab navigation

Tab list with real items.

![Tab navigation](shots/v2-06-tab-navigation.png)

#### Tab pinned card

Pinned card beside the tab list.

![Tab pinned card](shots/v2-07-tab-pinned-card.png)

#### Filter before

Lobby with every tag showing.

![Filter before](shots/v2-08-filter-before.png)

#### Filter after

Only LT3 and above.

![Filter after](shots/v2-09-filter-after.png)

#### Card styles

Backgrounds, borders and effects.

![Card styles](shots/v2-10-card-styles.png)

#### Moon card on the HUD

Styled card in game.

![Moon card on the HUD](shots/v2-12-moon-card-on-hud.png)

#### Settings: Tags

Essentials first.

![Settings: Tags](shots/v2-13-settings-tags.png)

#### Settings: Profile

Essentials first.

![Settings: Profile](shots/v2-14-settings-profile.png)

#### Settings: Profile, Advanced

Every setting, opened.

![Settings: Profile, Advanced](shots/v2-15-profile-advanced.png)

#### Config before and after

Tags tab compared.

![Config before and after](shots/v2-16-config-before-after.png)

#### Settings: Colours

Real items and logos.

![Settings: Colours](shots/v2-17-settings-colours.png)

#### Settings: Rules

Real items and logos.

![Settings: Rules](shots/v2-18-settings-rules.png)

#### Settings: Sharing

Real items and logos.

![Settings: Sharing](shots/v2-19-settings-sharing.png)

#### Highlight rules

In nametag, tab and chat.

![Highlight rules](shots/v2-20-highlight-rules.png)

#### Settings: search

Search across all tabs.

![Settings: search](shots/v2-21-settings-search.png)

#### First-run notice

With the logo download disclosure.

![First-run notice](shots/v2-22-first-run.png)

### Revision 1

#### Icons

Original 16x16 set for 21 kits plus 5 neutral list marks, hand-written in make_icons.py. Note the ink outline keeps them readable on bright sky.

![Icons](shots/00-icon-sheet.png)

#### Card A: Tallium Flat

Quiet slate panel in the corner, away from crosshair and hotbar. Real JokingDK data: hollow chips are retired, the small arrow means their peak was higher, PVPHQ was really down.

![Card A: Tallium Flat](shots/01-card-a-tallium-flat.png)

#### Card B: McPvP Bold

Same data as big saturated tiles you can read at a glance. Louder and larger than A, and it covers more of the sky.

![Card B: McPvP Bold](shots/02-card-b-mcpvp-bold.png)

#### Card C: Esports Card

Broadcast lower third in the bottom right, one column per list. Best tier large, next three kits small with a tier-coloured underline.

![Card C: Esports Card](shots/03-card-c-esports.png)

#### Card A: sparse profile

Real Marlowww, the Combat Grandmaster title holder: only ranked on MCTiers, all retired, so the card stays tidy with Not ranked rows.

![Card A: sparse profile](shots/04-card-a-sparse-marlowww.png)

#### Tab navigation

Hold Tab, move with the arrow keys, and the highlighted row is the player you will pin. You keep moving because no menu opens.

![Tab navigation](shots/05-tab-navigation.png)

#### Tab navigation: pinned card

Press Enter and the card sits beside the tab list. Enter or Esc closes it.

![Tab navigation: pinned card](shots/06-tab-pinned-card.png)

#### Filter: before

The TierTagger problem: almost everyone shows HT4, so the tags tell you nothing. Names and tiers here are invented samples.

![Filter: before](shots/07-filter-before.png)

#### Filter: after

Same lobby with only LT3 and above. The few players worth noticing now stand out.

![Filter: after](shots/08-filter-after.png)

#### Name highlight rules

HT1 turns purple and bold with a star, above HT3 turns red, both with a tag glow. It applies to nametag, tab list and chat.

![Name highlight rules](shots/09-highlight-rules.png)

#### Settings: Tags tab

Table of the five surfaces, with a collapsible inspector for the selected one and a live preview that shows how many tags survive your filter.

![Settings: Tags tab](shots/10-settings-tags.png)

#### Settings: Colours tab

Four presets, a colour for every tier, shared or per list, a full RGB picker and a preview of the nametag, tab list and card chips.

![Settings: Colours tab](shots/11-settings-colours.png)

#### Settings: Rules tab

Ordered rules with an editor. The preview shows which rule caught each sample player, so first-match-wins is easy to see, and the rule is repeated in plain words.

![Settings: Rules tab](shots/12-settings-rules.png)

#### Settings: Sharing tab

Named setups such as Casual and Tournament, plus a share code. Importing shows what would change before you commit.

![Settings: Sharing tab](shots/13-settings-sharing.png)

#### Settings: search

How 118 settings stay organised: seven tabs, collapsible groups, and one search across all of them with the setting's path and a live control on each result.

![Settings: search](shots/14-settings-search.png)

#### Card styles showcase

Six animated backgrounds including an original pixel-art moon, five borders, and sparkles and motes at different intensities. Everything stays inside the card.

![Card styles showcase](shots/15-card-styles.png)

#### Styled card in the game

The moon card on the real HUD. It stays in the corner, clear of crosshair and hotbar, and it is drawn only in the GUI, never in the world.

![Styled card in the game](shots/16-card-on-hud.png)

#### Moon card, animated

The showcase card as a seamless 8 second loop at 6 frames a second: twinkling stars, drifting cloud, sparkles and two shimmer comets running round the border. VS Code shows this animated PNG moving.

![Moon card, animated](shots/17-card-moon-animated.png)

#### Moon card, three frames

Three moments from the same loop, for viewers that show only still images.

![Moon card, three frames](shots/18-card-moon-frames.png)

#### Settings: Profile tab

Edit your own card with a live preview: armor per slot, trim pattern and material, background, border, effects, accent, featured kit and list. This replaces the old loadout editor. Preset choices only.

![Settings: Profile tab](shots/19-settings-profile.png)

#### First-run notice

A short, friendly disclosure of what leaves your game, the login promise, and an off switch.

![First-run notice](shots/20-first-run.png)
