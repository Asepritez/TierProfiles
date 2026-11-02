# Porting to every version TierTagger supports

Goal: a player on any version TierTagger runs on can use TierProfiles instead. Checked against
TierTagger's Modrinth page on 2 Nov 2026.

## What TierTagger ships

| Minecraft | TierTagger build | Loaders | Last updated |
|---|---|---|---|
| 26.3 | 2.7.0 | Fabric, Quilt, NeoForge | Sep 2026 |
| 26.2 | 2.6.0 | Fabric, Quilt, NeoForge | Jun 2026 |
| 26.1 to 26.1.2 | 2.5.1 | Fabric, Quilt, NeoForge | May 2026 |
| 1.21.11 | 2.4.1 | Fabric, Quilt | Mar 2026 |
| 1.21.9 to 1.21.10 | 2.3.2 | Fabric, Quilt | Mar 2026 |
| 1.21.6 to 1.21.8 | 2.2.3 | Fabric, Quilt | Mar 2026 |
| 1.21.5 | 2.1.4 | Fabric, Quilt | Mar 2026 |
| 1.21.4 | 2.0.4 | Fabric, Quilt | Mar 2026 |
| 1.21.2 to 1.21.3 | 1.99.4 | Fabric, Quilt | Mar 2026 |
| 1.21 to 1.21.1 | 1.98.4 | Fabric, Quilt | Mar 2026 |
| 1.20.5 to 1.20.6 | 1.10.5 | Fabric, Quilt | Feb 2025 |
| 1.20.3 to 1.20.4 | 1.8.7 | Fabric, Quilt | Feb 2025 |
| 1.20 to 1.20.2 | 1.7.8 | Fabric, Quilt | Feb 2025 |
| 1.18.2 to 1.19.4 | 1.6.5 | Fabric, Quilt | Sep 2024 |
| 1.18 to 1.18.1 | 1.2 | Fabric | Dec 2022 |

Fabric builds also run on Quilt, so Quilt costs nothing extra.

## How we build them

- **One core for all.** `mod/core` is plain Java 17 with no Minecraft in it: the data model, filters,
  tags, rules, card layout, animations, settings, share codes and both lookup paths. Its tests run
  once and cover every version. Since 2 Nov 2026 it compiles to Java 17 bytecode, because 1.18 to
  1.20.4 run on Java 17.
- **One Minecraft layer, many targets.** The Minecraft-facing code (`mod/src`, about 6,000 lines)
  moves to [Stonecutter](https://stonecutter.kikugie.dev/), which builds one source tree for many
  versions, with small `//? if >=1.21.6` blocks where the game's code differs.
- **NeoForge** for 26.x, as TierTagger does, through a thin loader layer over the same code.

## Where the game changed under us

| Version | What changes for us |
|---|---|
| 26.x | No obfuscation, `GuiGraphicsExtractor`, spear items. This is what we build today. |
| 1.21.6 to 1.21.11 | HUD layers through `HudElementRegistry`, 2D `Matrix3x2f` pose stack |
| 1.21.2 to 1.21.5 | Entity render states, so the nametag mixin changes |
| 1.21 to 1.21.1 | Mace exists. Older versions show the next best icon. |
| 1.20.5 to 1.20.6 | Item components (trims become a component), Java 21 |
| 1.20 to 1.20.4 | `GuiGraphics` arrives in 1.20. Trims and smithing templates are new. Java 17. |
| 1.18 to 1.19.4 | `PoseStack` and `GuiComponent`, no trims, so card armor has no trim rows |

Items a version doesn't have fall back to the closest item it does have, and settings for
features a version can't show (trims before 1.20) are hidden rather than broken.

## Order

1. 1.21.11, the version most PvP servers and clients are on.
2. The rest of 1.21 (1.21 to 1.21.10), which shares most of 1.21.11's code.
3. 26.1 and 26.2, next to the 26.3 build we have.
4. NeoForge for 26.x.
5. 1.20 to 1.20.6.
6. 1.18 to 1.19.4.

Each target gets the same in-game test run as 26.3, at three window sizes and GUI scales 2 to 4,
before it ships.
