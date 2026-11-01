package dev.tierprofiles.hud;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Where downloaded list logos live, in the config folder and never in the jar. */
public final class LogoStore {
    private LogoStore() {
    }

    public static Path file(String listId) {
        return FabricLoader.getInstance().getConfigDir().resolve("tierprofiles").resolve("logos").resolve(listId + ".png");
    }
}
