package dev.tierprofiles.core.settings;

import java.util.LinkedHashMap;
import java.util.Map;

/** Everything saved to disk: PC-wide settings plus every named profile. */
public final class ConfigFile {
    public static final String DEFAULT_PROFILE = "Default";
    public static final int MAX_PROFILES = 32;
    public static final int MAX_NAME_LENGTH = 32;

    public int version = 1;
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

        if (activeProfile == null || !profiles.containsKey(activeProfile)) {
            activeProfile = profiles.keySet().iterator().next();
        }
        return this;
    }

    public static boolean isValidName(String name) {
        return name != null && !name.isBlank() && name.length() <= MAX_NAME_LENGTH;
    }
}
