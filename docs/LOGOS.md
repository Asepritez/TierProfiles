# Logos, kit icons and textures

Everything here is loaded at runtime. **Do not bundle or commit any of it.** Mojang textures come from
the player's own game. List logos are downloaded from each list's own website and cached locally. If
a download fails, fall back to our neutral letter mark in
`mod/src/main/resources/assets/tierprofiles/textures/list/`.

All URLs were checked on 2026-11-02 with `User-Agent: TierProfiles/0.1 (+https://github.com/Asepritez/TierProfiles)`
and returned 200 unless noted. They can change without warning, so fail softly.

Minecraft's `NativeImage` reads **PNG only**. Prefer the PNG sources below. Formats marked `WebP`,
`SVG` or `ICO` need a decoder, or should be skipped in favour of a PNG or the fallback.

## List logos

| List id | Use | URL | Size | Format | Notes |
|---|---|---|---|---|---|
| `mctiers` | Logo mark | `https://mctiers.gg/logo.png` | 512×512 | PNG | Gold trophy. Used in the mockups. |
| `mctiers` | Small mark | `https://mctiers.gg/icon.png` | 192×192 | PNG | Same mark, smaller download. |
| `mcpvp` | Logo mark | `https://www.mcpvp.com/apple-touch-icon.png` | 180×180 | PNG | Crossed swords. Used in the mockups. Label McPvP as beta wherever it appears. |
| `mcpvp` | Wordmark | `https://www.mcpvp.com/LOGONEW.png` | 3007×831 | PNG | Too wide for a row. Use for headers only. |
| `pvptiers` | Logo mark | `https://pvptiers.com/hex-logo.png` | 2138×2138 | PNG | Orange hexagon. Use this, not the ICO. Scale it down once and cache it. |
| `pvptiers` | Small mark | `https://pvptiers.com/hex-logo.ico` | 256×256 | ICO | Used in the mockups. Not readable by `NativeImage`. |
| `subtiers` | Logo mark | `https://subtiers.net/subtiers-icon.webp` | 526×526 | WebP | Blue mark. **No PNG exists** (`/subtiers-icon.png` and `/icon.png` return 404), so this needs a WebP decoder or the fallback. |
| `pvphq` | Logo mark | `https://pvphq.com/assets/pvphq-logo.png` | 1652×1861 | PNG | Shield. Not square: fit it, do not stretch it. The PVPHQ tier API was down in testing (502), but this logo loads. |

## MCTiers title badges

| Title | URL | Size | Format |
|---|---|---|---|
| Combat Grandmaster | `https://mctiers.gg/titles/combat_grandmaster.webp` | 64×64 | WebP |
| Combat Master | `https://mctiers.gg/titles/combat_master.webp` | 64×64 | WebP |
| Combat Ace | `https://mctiers.gg/titles/combat_ace.webp` | 64×64 | WebP |

Other titles were not found: `combat_specialist`, `combat_cadet`, `combat_novice` and `rookie` all
return 404 at those guessed names. The three files above are large for their size (100 to 230 KB),
so they may be animated. Treat badges as optional.

## Official kit icons

Use vanilla items first (see below). These are options where a list's own art looks better, or where
there is no flat vanilla item.

### McPvP, `https://www.mcpvp.com/rankings/<name>`

| File | Size | Format | Notes |
|---|---|---|---|
| `sword.png` | 161×161 | PNG | Custom bold art |
| `mace.png` | 171×170 | PNG | Custom bold art |
| `spear.png` | 240×240 | PNG | Custom bold art |
| `pot.png` | 128×176 | PNG | Custom bold art |
| `uhc.png` | 186×151 | **WebP** | Named `.png` but the bytes are WebP. Used for UHC in the mockups. |
| `crystal.png` | 164×164 | PNG | Custom bold art |
| `early.png` | 166×184 | PNG | Custom bold art |
| `shield.png` | 168×210 | PNG | Custom bold art |
| `netherite-sword.png`, `ender-pearl.png`, `bow.png`, `cart.png`, `creeper.png`, `diamond-chestplate.png`, `netherite-chestplate.png` | 16×16 | PNG | Pixel-match the default game textures. Use the game's own texture instead. |

### MCTiers, `https://mctiers.gg/tier_icons/<name>.svg`

`overall`, `sword`, `axe`, `mace`, `nethop`, `pot`, `smp`, `uhc`, `vanilla`. All SVG, so they need a
rasteriser. Skip unless one is added.

### SubTiers, `https://subtiers.net/assets/<file>`

| File | Size | Format |
|---|---|---|
| `bed-20260909.png` | 750×750 | PNG. Used for Bed in the mockups. |
| `minecart-20260909-v2.png` | 750×750 | PNG |
| `og_vanilla-20260909.png` | 750×750 | PNG |
| `trident-20260909.png` | 1024×1024 | PNG |
| `elytra-20260909.png` | 750×750 | PNG |
| `dia_smp-20260909.png` | 750×750 | PNG |
| `dia_crystal-20260909.png` | 1024×1024 | PNG |
| `debuff-20260909.png` | 2000×2000 | PNG |
| `manhunt-20260909.png` | 750×750 | PNG |
| `speed-116175c6.svg`, `creeper-2cbc5b3a.svg`, `bow-0b52585f.svg`, `overall-ca77dd12.svg` | n/a | SVG |

The file names contain dates or hashes, so they will change when the site is rebuilt. Read them from
the site, or treat a 404 as "use the fallback".

## Vanilla textures for 26.3

Paths are inside the client jar. At runtime use the game's own resources, not these files.

| Use | Path |
|---|---|
| Moon phases | `assets/minecraft/textures/environment/celestial/moon/<phase>.png`, 32×32. Phases: `full_moon`, `waning_gibbous`, `third_quarter`, `waning_crescent`, `new_moon`, `waxing_crescent`, `first_quarter`, `waxing_gibbous`. Each file is opaque: an 8×8 moon at about (12,12) with its blue glow baked in, on black. Draw it additively, as the game does, or the black square shows. Old versions used a single `environment/moon_phases.png`, which is gone. The mockups use `full_moon`. |
| Sun | `assets/minecraft/textures/environment/celestial/sun.png` |
| Celestial atlas | `assets/minecraft/atlases/celestials.json`, so these are atlas sprites at runtime |
| Clouds | `assets/minecraft/textures/environment/clouds.png` |
| Item textures | `assets/minecraft/textures/item/<name>.png` |
| Potion tint layer | `assets/minecraft/textures/item/potion_overlay.png` |
| Shield | `assets/minecraft/textures/entity/shield/shield_base_nopattern.png`. The file `base.png` beside it is a blank white pattern layer. The item is a 3D model, so `renderItem` is best. |
| Armor | `assets/minecraft/textures/entity/equipment/humanoid/<material>.png`, and `humanoid_leggings/<material>.png` for leggings. Chain is `chainmail`. |
| Armor trims | `assets/minecraft/textures/trims/entity/humanoid/<pattern>.png`, and `humanoid_leggings/<pattern>.png`. Patterns: `bolt`, `coast`, `dune`, `eye`, `flow`, `host`, `raiser`, `rib`, `sentry`, `shaper`, `silence`, `snout`, `spire`, `tide`, `vex`, `ward`, `wayfinder`, `wild`. |
| Hearts and food | `assets/minecraft/textures/gui/sprites/hud/heart/full.png`, `food_full.png` |

### Kit to item

Sprite `item/<name>` unless noted. The game's `renderItem` is the better route for all of them.

| Kit id | Item |
|---|---|
| `sword` | `netherite_sword` |
| `axe` | `netherite_axe` |
| `mace` | `mace` |
| `spear` | `netherite_spear` (`diamond_spear` and `iron_spear` also exist) |
| `pot` | `splash_potion`, tinted red through `potion_overlay` |
| `nethop` | `netherite_helmet` |
| `smp` | `ender_pearl` |
| `vanilla`, `crystal`, `end_game` | `end_crystal` |
| `shield` | `shield` |
| `bow` | `bow` |
| `cart` | `tnt_minecart` |
| `creeper` | `creeper_spawn_egg` |
| `diamond_smp` | `diamond_chestplate` |
| `early_game` | `golden_apple` |
| `late_game` | `netherite_chestplate` |
| `trident` | `trident` |
| `elytra` | `elytra` |
| `speed` | `potion`, tinted cyan through `potion_overlay` |
| `uhc` | No flat item. Mockups use McPvP's `uhc.png`. |
| `bed` | No flat item (the bed item is a 3D model). `renderItem` on `red_bed` works in game. Mockups use SubTiers' art. |

## Programmer Art: which textures match McPvP

The spec asks for Classic, from Programmer Art, as the default icon style. **The comparison says
otherwise.** McPvP's seven 16×16 icons are pixel-identical to the **default 26.3 textures**:

| McPvP icon | Default 26.3 | Programmer Art |
|---|---|---|
| `bow` | 0% different | no texture in the pack |
| `cart` | 0% | 41% different |
| `creeper` | 0% | 52% different |
| `diamond-chestplate` | 0% | 55% different |
| `ender-pearl` | 0% | 14% different |
| `netherite-chestplate` | 0% | no texture in the pack |
| `netherite-sword` | 0% | no texture in the pack |

So the mockups use the default textures. Programmer Art also has no netherite, mace, spear, trident
or bow item textures, and no moon, so a Classic set would be a mix. Suggestion: make the default icon
style the normal game textures, and offer Programmer Art as the optional older look.

The pack is not in the client jar. It is the asset `minecraft/resourcepacks/programmer_art.zip`
(689,570 bytes, hash `13ab19f0b4dfe3571f8247561f08ea5d98b0dcfa`), and the game offers it as a built-in
resource pack.
