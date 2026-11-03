package dev.tierprofiles;

import dev.tierprofiles.core.config.ConfigStore;
import dev.tierprofiles.core.net.HttpTransport;
import dev.tierprofiles.core.net.PlayerSearch;
import dev.tierprofiles.core.net.ProfileService;
import dev.tierprofiles.core.net.CosmeticsService;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Cosmetics;
import dev.tierprofiles.core.settings.Settings;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared state for the mod: the current config and the lookup service. */
public final class TierProfiles {
    public static final String MOD_ID = "tierprofiles";
    public static final Logger LOGGER = LoggerFactory.getLogger("TierProfiles");

    private static final String USER_AGENT = "TierProfiles/"
            + FabricLoader.getInstance().getModContainer(MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev")
            + " (+https://github.com/Asepritez/TierProfiles)";

    private static ConfigStore store;

    // Volatile so the render thread sees a new config as soon as Apply publishes it.
    private static volatile ConfigFile config;
    private static ProfileService profiles;
    private static volatile String detectedKit;
    private static final Cosmetics DEFAULT_LOOK = new Cosmetics().sanitize();
    private static CosmeticsService cosmetics;
    private static PlayerSearch playerSearch;

    private TierProfiles() {
    }

    static void start() {
        store = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("tierprofiles.json"));
        config = store.load();
        HttpTransport transport = new HttpTransport(USER_AGENT);
        profiles = new ProfileService(transport, () -> config.global, System::currentTimeMillis,
                FabricLoader.getInstance().getConfigDir().resolve("tierprofiles-cache.json"));
        profiles.start();

        ExecutorService background = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "TierProfiles cards");
            thread.setDaemon(true);
            return thread;
        });
        cosmetics = new CosmeticsService(transport, () -> config.global.cardStyleServer(), new MojangJoiner(), background, System::currentTimeMillis);

        ExecutorService searching = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "TierProfiles player search");
            thread.setDaemon(true);
            return thread;
        });
        playerSearch = new PlayerSearch(transport, searching, profiles);
    }

    public static PlayerSearch playerSearch() {
        return playerSearch;
    }

    public static ConfigFile config() {
        return config;
    }

    public static Settings settings() {
        return config.active();
    }

    public static ProfileService profiles() {
        return profiles;
    }

    public static CosmeticsService cosmetics() {
        return cosmetics;
    }

    /** Sends your card to the server, which is the only time it leaves your game. */
    public static void saveCosmetics() {
        if (!ProfileService.isAllowed(config.global)) {
            return;
        }
        cosmetics.save(config.global.cosmetics, Minecraft.getInstance().getUser().getName());
    }

    /** How a player's card looks. Yours comes from your settings, and others from the server. */
    public static Cosmetics cosmeticsFor(UUID uuid) {
        if (Minecraft.getInstance().isLocalPlayer(uuid)) {
            return config.global.cosmetics;
        }
        Cosmetics theirs = cosmetics.get(uuid);
        return theirs != null ? theirs : DEFAULT_LOOK;
    }

    /** The kit your inventory suggests, or null. */
    public static String detectedKit() {
        return detectedKit;
    }

    static void setDetectedKit(String kit) {
        detectedKit = kit;
    }

    /** Makes a new config live and saves it. Saving failing shouldn't stop the game. */
    public static void publish(ConfigFile updated) {
        boolean serverChanged = !updated.global.serverUrl.equals(config.global.serverUrl) || updated.global.source != config.global.source
                || updated.global.cardStyles != config.global.cardStyles;
        boolean lookupsOff = !updated.global.lookupsEnabled;
        config = updated;
        if (serverChanged || lookupsOff) {
            profiles.clear();
            cosmetics.clear();
        }
        try {
            store.save(updated);
        } catch (IOException e) {
            LOGGER.warn("Could not save settings", e);
        }
    }
}
