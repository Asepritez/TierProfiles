## What you need

| Minecraft | Fabric Loader | Java | Jar to use |
|---|---|---|---|
| **1.21.11** | 0.17.3 or newer | 21 or newer | `tierprofiles-<version>+mc1.21.11.jar` |
| **26.3** | 0.20.1 or newer | 25 or newer | `tierprofiles-<version>+mc26.3.jar` |

- **Required:** [Fabric API](https://modrinth.com/mod/fabric-api) for the same Minecraft version.
- **Optional:** [Mod Menu](https://modrinth.com/mod/modmenu), which adds a settings button to the mods list.

The launcher's Java is usually new enough. If the game says the mod needs a newer Java, update it in your launcher's settings.

## Installing

**With a launcher that supports Modrinth** (Modrinth App, Prism, and others): search for TierProfiles and click install. The launcher picks the right jar and installs Fabric API with it.

**By hand:**
1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version.
2. Download the TierProfiles jar that matches your Minecraft version, and Fabric API.
3. Put both jars in your `.minecraft/mods` folder.
4. Start the game with the Fabric profile.

## Updating

Delete the old TierProfiles jar from `mods` before adding the new one. Two copies at once won't load. Your settings are kept.

The version is in the file name, for example `tierprofiles-0.5.0+mc1.21.11.jar` is version 0.5.0 for Minecraft 1.21.11.

## Where settings are saved

Everything lives in `.minecraft/config/tierprofiles.json`. Deleting that file starts you fresh, including the welcome page.
