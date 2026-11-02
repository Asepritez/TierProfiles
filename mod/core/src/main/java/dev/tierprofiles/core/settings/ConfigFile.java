package dev.tierprofiles.core.settings;

import java.util.LinkedHashMap;
import java.util.Map;

/** Everything saved to disk: PC-wide settings plus every named profile. */
public final class ConfigFile {
    public static final String DEFAULT_PROFILE = "Default";
    public static final int MAX_PROFILES = 32;
    public static final int MAX_NAME_LENGTH = 32;

    /** Bumped when saved settings need a one-time change on load. */
    public static final int CURRENT_VERSION = 2;

    public int version = CURRENT_VERSION;
    public Global global = new Global();
    public String activeProfile = DEFAULT_PROFILE;
    public Map<String, Settings> profiles = new LinkedHashMap<>();

    public ConfigFile() {
        profiles.put(DEFAULT_PROFILE, new Settings());
    }

    public Settings active() {
        return profiles.get(activeProfile);
    }

    public ConfigFile sanitize() {
        global = global == null ? new Global() : global;
        global.sanitize();

        Map<String, Settings> clean = new LinkedHashMap<>();
        if (profiles != null) {
            for (Map.Entry<String, Settings> entry : profiles.entrySet()) {
                if (isValidName(entry.getKey()) && entry.getValue() != null && clean.size() < MAX_PROFILES) {
                    clean.put(entry.getKey(), entry.getValue().sanitize());
                }
            }
        }
        if (clean.isEmpty()) {
            clean.put(DEFAULT_PROFILE, new Settings());
        }
        profiles = clean;

        if (version < 2) {
            // Version 1 put letter badges such as "MP" on the aim card, which read as noise.
            for (Settings settings : profiles.values()) {
                settings.surface(Surface.LOOK_AT).listLogo = false;
            }
        }
        version = CURRENT_VERSION;

        if (activeProfile == null || !profiles.containsKey(activeProfile)) {
            activeProfile = profiles.keySet().iterator().next();
        }
        return this;
    }

    /**
     * Everything back to how a fresh install starts, except the answer to the privacy
     * notice, which the player already gave.
     */
    public static ConfigFile defaultsKeepingConsent(ConfigFile current) {
        ConfigFile fresh = new ConfigFile();
        fresh.global.noticeSeen = current.global.noticeSeen;
        fresh.global.lookupsEnabled = current.global.lookupsEnabled;
        return fresh.sanitize();
    }

    public static boolean isValidName(String name) {
        return name != null && !name.isBlank() && name.length() <= MAX_NAME_LENGTH;
    }
}
