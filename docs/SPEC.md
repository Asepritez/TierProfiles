# TierProfiles spec

TierProfiles is a client-side Fabric mod for Minecraft PvP players. It shows a player's tiers from
every major tier list in one place, and lets you decide exactly what you see.

This file is the shared contract between the mod, the server and the design drafts. If you need to
change something here, change it here too.

## Goals

1. **One profile across every list.** MCTiers, McPvP, PvPTiers, SubTiers and PVPHQ on one card.
2. **Heavy customization.** The main complaint about TierTagger is that it's too rigid. Everyone
   having HT4 above their head is noise, so players must be able to filter it out.
3. **Works mid-fight.** Looking someone up must never stop you moving.
4. **Free to run.** No paid server until the mod has proven itself.

## Data sources

All of these are unofficial and undocumented. Tested 2026-11-01. Any of them can change, so every
provider must fail softly: one list going down must never break the others.

| List | Lookup | Notes |
|---|---|---|
| MCTiers | `https://mctiers.gg/api/profile/<uuid>` | Also `/api/profile/by-name/<name>`. Accepts dashed or plain UUID. Returns `rankings`, `region`, `points`, `overall`, `badges`. Sends `max-age=600`. The old `mctiers.com` is dead (registrar hold). |
| McPvP | `https://www.mcpvp.com/tiers/search?q=<name>` | Name search, not exact: it matches substrings, so filter results by UUID. Query must be 3 to 16 characters. Capped at 50 results sorted by points, so a full page with no match means "unknown", not "unranked". Returns `points`, `region`, `country`, `kitRanks`, `kitPeakRanks`, `kitRetired`, `trend`. Beta tiers will reset. Add `&version=legacy` for the old list (not used). |
| PvPTiers | `https://pvptiers.com/api/profile/<uuid>` | UUID must have **no dashes**; dashed returns 400. |
| SubTiers | `https://subtiers.net/api/v2/profile/<uuid>` | Dashed UUID. Returns `rankings`, `region`, `points`, `overall`, `badges`. |
| PVPHQ | `https://pvphq.com/api/ranked/<uuid>` | Down for most of 2026-11-01 (hung, then 502), answering again on 2026-11-02 and verified against real replies. Returns `{data: {LADDER: {rating, peakRating, gamesPlayed, grantedTier, ...}}, globalPosition, rank}`. `globalPosition` is 0-based and `99999` means no position. Unknown players get 200 with empty `data`. Ladders include `SPEAR_MACE`, `HT_CART`, `DIAMOND_POT`, `NETHERITE_OP`. Needs a hard timeout. |

MCTiers-style rankings use `{tier, pos, peak_tier, peak_pos, attained, retired}`, where `pos` 0 is
High and 1 is Low, so `tier 3, pos 1` is LT3. McPvP uses strings such as `HT2`, and has an extra
`MT` (mid) and `ST` (lowest) tier, with levels 1 to 6. `peak_tier` and `peak_pos` can be null. All lists
answer 404 for a player they don't know, except McPvP, which returns an empty result, and PVPHQ,
which returns 200 with empty `data`.

McPvP is mainstream but new, and some of its tiers look inflated, so it is included but must not be
the default top-priority list. MCTiers comes first by default, and the UI labels McPvP as beta.
`mcpvp.club` is the practice server, not a data source. `mcpvp.gg` had no website when checked.

Titles come from points on the MCTiers ladder: Grandmaster 400+, Master 250+, Ace 100+,
Specialist 50+, Cadet 20+, Novice 10+, Rookie 1+. McPvP has no titles of its own, so TierProfiles
applies this ladder to McPvP points and calls it a TierProfiles title.

## Server

Cloudflare Workers on the free plan, in `server/`, in TypeScript. No payment needed. Limits: 100,000
requests a day, 10 ms CPU per request, 50 outgoing requests per request. D1 database: 5 GB, 5 million
row reads and 100,000 row writes a day. KV allows only 1,000 writes a day, so **do not use KV as the
cache**: use the Workers Cache API.

- `GET /v1/profile/<uuid>?name=<name>` returns one merged profile.
- `POST /v1/profiles` takes `{"players": [{uuid, name}]}`, up to 8, and returns `{"profiles": [...]}`.
- `GET /v1/cosmetics/<uuid>` returns `{uuid, cosmetics}`, where `cosmetics` is null for a player with
  no saved style.
- `POST /v1/auth/challenge` returns `{challenge, serverId, expires}`. The mod passes `serverId` to
  Mojang's `joinServer`. Challenges are HMAC-signed and last 2 minutes, so no storage is needed.
- `POST /v1/auth/verify` takes `{name, challenge}`, asks Mojang's `hasJoined`, and returns
  `{token, uuid, expires}` (15 minutes), or 403 if Mojang didn't confirm. The UUID comes only from
  Mojang's answer.
- `PUT /v1/cosmetics` with `Authorization: Bearer <token>` stores the body for the token's own UUID.
  Every value must be on the allowlist in `server/src/cosmetics.json`, which a mod test keeps in step
  with the mod. One save per player per 10 seconds (429 otherwise). Stored in D1, table `cosmetics`.
- Identity needs an `AUTH_SECRET` (`wrangler secret put AUTH_SECRET`, or `.dev.vars` locally). Without
  it, identity routes answer 503. `MOJANG_SESSION_URL` exists only for local tests against a mock.
- Cache a good result per list for 30 minutes, and "not ranked" for 2 hours.
- A provider that fails returns `{status: "unavailable", reason}` for that list, never a 500.
- Only version 4 UUIDs are accepted; anything else is a 400.

**Budget.** Cache calls share the 50-per-request quota with `fetch`, so 8 players on 5 lists can't
have one cache entry per list (40 match + 40 fetch + 40 put = 120). The server keeps **one entry per
player**, with an expiry per list inside it, and spends a budget of 48 subrequests. A full cold batch
is 8 matches, 8 puts and 32 fetches, which covers every list but PVPHQ. Lists are served in the order
MCTiers, McPvP, PvPTiers, SubTiers, PVPHQ, and a list that misses out reports `reason: "busy"` and is
not cached, so the next request fills the gap.

**Failure handling.** Each list call times out after 3.5 seconds. A failed call is remembered for one
minute. Two failures in a row stop calls to that list for a minute (`circuit_open`).

**Cache API caveat.** Cloudflare documents working cache operations for custom domains and does not
say they work on `workers.dev`. So a bounded in-memory map sits in front of the Cache API. If we
deploy to `workers.dev` with no domain, check whether entries survive. If not, the server still works
but relies on memory alone and calls the lists more often.

Merged profile:

```json
{
  "uuid": "1246a0ec-662b-45de-bb0b-d68cb1cce800",
  "name": "JokingDK",
  "lists": {
    "mctiers": {
      "status": "ok", "region": "EU", "country": null, "points": 102, "overall": 56,
      "tiers": [{"kit": "sword", "rank": "LT3", "peak": "HT3", "attained": 1772946009, "retired": false}]
    },
    "mcpvp": {"status": "unranked"},
    "pvphq": {"status": "unavailable", "reason": "timeout"}
  }
}
```

`lists` always has all five keys. `rank` and `peak` are `HT`, `MT`, `LT` or `ST` plus a level. `peak`
and `attained` may be null. `rating` and `games` appear on PVPHQ tiers. Kit ids are normalized so one
gamemode has one name: `vanilla` becomes `crystal`, `nethop` and `netherite-pot` become `neth_pot`,
`cart` becomes `minecart`, `diamond-smp` becomes `dia_smp`, and dashes become underscores. Unknown kits
pass through. Titles are not sent: the mod works them out from `points`.

Unavailable reasons: `timeout`, `network`, `http_<code>`, `bad_json`, `bad_shape`, `circuit_open`,
`busy`, `name_required`, `name_too_short`, `search_truncated`.
- Send a clear `User-Agent` naming the project and repo URL.

The mod's server URL is a config value, default `http://127.0.0.1:8787` until we deploy, so it can
run against `wrangler dev`.

## Mod

- Fabric, Minecraft 26.3, Java 25. Loader 0.20.1, Fabric API 0.165.0+26.3, Mod Menu 21.0.0
  (optional).
- Versions: every version TierTagger supports, so players never have to choose. On 2 Nov 2026 that
  was 1.18 to 26.3 on Fabric, Quilt and NeoForge. The core module is plain Java 17 so one core
  serves them all; only the Minecraft-facing layer differs per version.
- Lookups go straight to the five list sites by default ("Look up from: Lists"), so the mod works
  with nothing else running. "Server" mode uses the TierProfiles server instead, which adds shared
  card styles. `core/net/DirectLists` and `server/src/providers` do the same work and change
  together.
- Package `dev.tierprofiles`, mod id `tierprofiles`.
- Client-side only. No config library: a custom flat UI in the style of Tallium (see Credits).
- Only look up players the server has already put in the tab list, and skip UUIDs that are not
  version 4 (they are NPCs or fake entries).
- Nothing that automates combat, and nothing that reveals hidden players.

### Surfaces

Each one is switched and configured independently:

| Surface | What it shows |
|---|---|
| Nametag | Tag next to the name above a player's head |
| Tab list | Tag next to the name in the tab list |
| Chat | Tag beside the sender's name. Off by default. |
| Look-at card | Small card when you aim at a player |
| Profile overlay | The full card |

### Customization

This is the headline feature. All of it is staged until Apply, as in Tallium.

**Filtering**
- Minimum tier: hide tags below a chosen tier, e.g. "only LT3 and above". Per surface.
- Per-list minimum, because HT4 means different things on different lists.
- Hide unranked, hide retired, show peak instead of current, hide your own tag.
- Pick which gamemodes count, or use "highest overall" or auto-detect from your inventory.
- List priority: which list wins when a tag only has room for one.
- **Lists tab:** click a list's logo to remove that list everywhere (tags, names, cards, rank,
  points, title), and click a kit to remove that kit everywhere. Someone who never plays SMP never
  sees SMP. A list row whose only kits are hidden says "Kits hidden", never "Unranked".

**Styling**
- Colour for every tier, HT1 to LT5, per list or shared. Full RGB picker.
- Tier presets: Default, Heat, Mono, Colourblind-safe.
- Toggle logos: gamemode icon, list logo, region. Toggle each on its own.
- Tag format: position (before or after the name), brackets, separator, short or long.
- Icon set: TierProfiles (default) or vanilla items.
- Scale and background opacity for the look-at card and overlay.

**Name highlight rules**
- Rules of the form "if tier is at or above X on list Y, then…": recolour the name, gradient,
  bold, glow outline around the tag, or a star prefix.
- Rules run top to bottom; the first match wins.
- Example: above HT3 turns the name red; HT1 turns it purple and bold.

**Sharing**
- Named profiles for your settings, so you can keep "Tournament" and "Casual".
- Share code: export all settings as text, import a friend's. Tallium has this idea.

### Controls

- Hold **Tab**, use the **arrow keys** to move through players, press **Enter** to pin their card.
  Minecraft draws the tab list with no menu open, so read keys without opening a `Screen`.
- The profile is a **HUD overlay, not a Screen**: opening a Screen releases movement keys and would
  stop you mid-fight. Tallium's `PlayerPopup` proves this pattern.
- **While in Tab** (Profile tab): what shows as you move through players before pinning anyone.
  Small card (default) is the look-at card for the selected player. Big card shows their full card,
  swapping as you move; letting go of Tab puts it away, and Enter keeps it. Nothing waits for Enter.
- Enter or Escape closes it. It also closes after a configurable time ("Stays open", 0 to 120
  seconds, or until closed), on the Profile tab's essentials.
- **All ranks** (Right Shift by default): on an open card it switches to every kit on every list,
  wrapping rows onto more lines, and back again. With Tab held it opens the selected player straight
  into All ranks; otherwise it opens whoever the look-at card shows. It never drops a kit: if the
  screen is too small it shrinks the text as a last resort.
- F12 by default opens settings. All keys are rebindable.

### Profile card

The default blends the esports card with the sparse profile, and stays minimal. Top of the card:
player's 3D model, name, region, title, **highest rank** and **total points**. Below: a row per list
with tier chips and vanilla kit icons. No coloured underlines beneath icons.

Each tier shows the date it was achieved, plus peak and retired state. A "Card detail" setting
controls how much appears: Minimal, Standard (default) or Full. A "Last column" setting picks
what follows the third kit in Standard: the Date (default), a 4th tier in the same slot grid, or
Nothing for a narrower card. The moon always sits 24 pixels left of the rank, wherever the rank
moves.

**Position.** The player chooses where the card sits: nine anchors (corners, edge centres, centre),
fine X and Y offsets, and dragging it on a live preview. It stays inside the screen, and the card
glides in from the nearest edge of wherever it's placed.

**Alignment is a hard rule.** All card content sits on one fixed column grid, shared by the design
drafts and the mod:

| Column | Contents | Rule |
|---|---|---|
| Model | 3D model | Fixed width, full card height |
| List | Logo, then name | Fixed logo slot, left-aligned text |
| Kit 1, 2, 3 | Icon, then tier chip | Fixed-width slots wide enough for the widest chip |
| Date, or Kit 4 | Achieved date, or a 4th kit | Date right-aligned to the card's inner edge |

- Every list row uses the same columns, including rows with no tiers. Empty slots stay empty.
- Column widths are measured from the font, so text can never touch or overlap its neighbour.
- Retired and peak markers live inside the chip's own slot, never spilling into the next one.
- One line-height, one padding value, one gap value. The top strip's right edge meets the date column.
- The date shown belongs to the row's best tier. Say so in the tooltip or Full detail.
- A list with no result shows muted **Unranked** in the Kit 1 column. Never "offline". If the list
  is down, the row still reads Unranked, and the real status is on the Advanced > Diagnostics page.
- Check every layout at 854×480, 1280×720 and 1920×1080, GUI scales 2, 3 and 4. If it can't fit,
  drop detail level rather than overlap.

**Animation is a priority.** Cards never just appear:
- The card glides smoothly into the frame with easing, and glides out on close.
- Rows and chips stagger in just after the card lands.
- Moving selection in the tab list slides smoothly, and profile backgrounds and effects keep
  animating.
- Settings cover animation style (Glide, Fade, Scale, Off) and speed, and "reduce motion" turns
  them off. Animation is time-based, never tied to frame rate, and must not block input.

### Your profile (visual only)

Every customization here is cosmetic and appears **only inside TierProfiles' own GUI**: the profile
card and the settings preview. Nothing changes the player's real armor, skin, hitbox, or anything in
the world. Nothing touches gameplay or sends anything to the Minecraft server, so it cannot obstruct
a fight or get anyone banned. This is a hard rule.

Players edit their own profile in the in-game config, on a **Profile** tab:
- Card model: armor material, trim pattern and trim material per piece, so the helmet and the
  chestplate can carry different trims. Pick a piece, or All. Purely a visual on the card. Older
  saved cards with one shared trim load with that trim on every piece.
- Lists and kits on my card: leave a list (say one you're unranked on) or kits off your own card,
  for you and everyone who sees it. This adds to each viewer's own filters and never changes them.
- Card background: animated scenes such as moon and night sky, starfield, nebula, embers, snow, plain.
- Card border: thin, double, glowing, animated shimmer, corner ornaments.
- Effects: sparkles, drifting particles, floating motes, with an intensity slider and an off switch.
- Accent colour, and which kit and list to feature.
- Preset choices only, no free text for now, so there is nothing to moderate.

All effects are drawn in the GUI, respect a global "reduce motion" setting, and must not cover the
crosshair or hotbar. Stored as a small JSON object on the server, so other mod users see it on your
card. Some options may later be earned from tiers. Nothing is sold.

### Identity (no linking)

No website, no Microsoft login screen, no code to paste. If you're logged in to Minecraft, you can
edit your own profile:

1. The mod asks our server for a random challenge.
2. The mod calls Minecraft's own `joinServer` session call with that challenge, the same silent step
   the game does whenever you join an online server.
3. Our server asks Mojang's `sessionserver.mojang.com/session/minecraft/hasJoined` whether that
   username really did that, and gets back the verified UUID.
4. Our server returns a short-lived token that allows saving that UUID's profile.

The player's access token goes **only to Mojang**, never to our server. Say this plainly in the
README and the first-run notice. Offline or cracked accounts can't save a profile, but can view.

### Help on hover

Every setting and every tab on the rail explains itself: hover for a quarter of a second and a
vanilla-style tooltip beside the mouse says what it does in one or two plain sentences. It fades in,
stays on screen, and is drawn above everything.

## Design language

Based on Tallium's look. Dark, flat, sharp corners, thin borders, no vanilla stone textures. Not too
bright. The default profile card blends Card A's compact rows with Card C's esports presentation.
Reference sites: `mctiers.gg` and `mcpvp.com`.

The config should feel unlike any other mod's config: distinctive, Minecraft-flavoured and pleasing,
with deep customization. Keep a live preview on screen wherever a setting changes appearance. The
same logos, vanilla icons, spacing and components appear on every tab and every screen.

The config must not feel overwhelming. Each tab opens on a few essentials, with a clear "Advanced"
section for the rest, and search finds everything.

| Token | Value |
|---|---|
| Background | `#090B0C` at 90% |
| Panel | `#191919` at 92% |
| Border | `#666666` |
| Row / alternate | `#101010` / `#303030` |
| Selected | `#3E4A50` |
| Text / muted | `#DDDDDD` / `#AAAAAA` |
| Accent | `#68B5D5` |
| Good / bad | `#77D877` / `#FF7777` |

Tier chip default colours: HT1 `#FFCF4A`, LT1 `#E6B422`, HT2 `#D0D6E0`, LT2 `#9FA8B8`, HT3 `#E08A4F`,
LT3 `#B8703F`, HT4 `#7FB069`, LT4 `#5E8A4E`, HT5 `#8A8F98`, LT5 `#6A6F78`.

Layout unit: one Minecraft GUI pixel. Control height 18, row height 19, gutter 6 or 10.

## Icons and rights

The look must be vanilla and official, so lists are easy to tell apart at a glance.

- **Vanilla first.** Use the player's own game textures at runtime wherever possible: kit icons from
  real items with `renderItem`, the card moon from the game's own moon texture, and armor and trims
  on the real player model. An "Icon style" setting offers Default, which matches McPvP's small
  icons and is the default, and Classic, from the game's built-in Programmer Art pack, which has
  no netherite, mace, spear or trident items and so falls back to Default for those.
- **Kit icons** match what the lists use: sword is a netherite sword, plus mace, potion, ender pearl,
  golden apple, shield, bow, creeper spawn egg, minecart with TNT, diamond and netherite chestplates.
- **Official list logos** are downloaded at runtime from each list's own website and cached locally.
  Sources are in `docs/LOGOS.md`. If one can't be fetched, fall back to our neutral letter mark.
- **Never commit** Mojang textures or third-party logos to this repo, and never bundle them in the
  jar. Runtime loading gives the same look without redistributing anyone's files.
- Our own pixel icons remain as the final fallback, and for anything with no vanilla equivalent.

## Privacy

Modrinth's rules require clear disclosure of data sent to a remote server. Say in the README, on
the Modrinth page and in a first-run notice that the mod sends the UUIDs and names of players in
your tab list to the five list websites (or, in Server mode, to the TierProfiles server), and in
Server mode your own card style when you choose to save it. Send nothing else. The mod also downloads list logos directly from those lists' websites. Your Minecraft
access token only ever goes to Mojang. Include a setting to turn lookups off.

## Credits

The UI style and the overlay approach come from [Tallium](https://github.com/5sig2/Tallium) by
5sig2 (MIT). Any code copied or adapted from it must keep its copyright notice in
`THIRD_PARTY_NOTICES.txt`. Do not copy code from TierTagger (MPL-2.0) or Tiers (GPL-3.0).
