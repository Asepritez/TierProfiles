package dev.tierprofiles.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Settings;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Staged editing: the settings screen changes a private copy, and nothing affects
 * the game until Apply. Discard throws the copy away.
 *
 * Applying swaps in a new object instead of changing the old one, so the render
 * thread only ever sees a complete config, never one half way through a change.
 */
public final class ConfigEditor {
    private ConfigFile live;
    private ConfigFile staged;

    public ConfigEditor(ConfigFile live) {
        this.live = live;
        this.staged = ConfigStore.copy(live);
    }

    public ConfigFile live() {
        return live;
    }

    /** The copy to edit. */
    public ConfigFile staged() {
        return staged;
    }

    public boolean isDirty() {
        return !json(staged).equals(json(live));
    }

    /** How many individual values differ from what's live. */
    public int changeCount() {
        return differences(ConfigStore.GSON.toJsonTree(live), ConfigStore.GSON.toJsonTree(staged));
    }

    private static int differences(JsonElement a, JsonElement b) {
        if (a instanceof JsonObject first && b instanceof JsonObject second) {
            Set<String> keys = new HashSet<>(first.keySet());
            keys.addAll(second.keySet());
            int count = 0;
            for (String key : keys) {
                count += differences(first.get(key), second.get(key));
            }
            return count;
        }
        return Objects.equals(a, b) ? 0 : 1;
    }

    /** Returns the new live config, which the caller then saves and publishes. */
    public ConfigFile apply() {
        staged.sanitize();
        live = staged;
        staged = ConfigStore.copy(live);
        return live;
    }

    public void discard() {
        staged = ConfigStore.copy(live);
    }

    private static String json(ConfigFile config) {
        return ConfigStore.GSON.toJson(config);
    }

    public boolean createProfile(String name, Settings source) {
        if (!ConfigFile.isValidName(name) || staged.profiles.containsKey(name)
                || staged.profiles.size() >= ConfigFile.MAX_PROFILES) {
            return false;
        }
        staged.profiles.put(name, ConfigStore.copy(source));
        return true;
    }

    /** The last profile can't be deleted, because something must always be active. */
    public boolean deleteProfile(String name) {
        if (staged.profiles.size() <= 1 || !staged.profiles.containsKey(name)) {
            return false;
        }
        staged.profiles.remove(name);
        if (name.equals(staged.activeProfile)) {
            staged.activeProfile = staged.profiles.keySet().iterator().next();
        }
        return true;
    }

    public boolean renameProfile(String from, String to) {
        if (!ConfigFile.isValidName(to) || staged.profiles.containsKey(to) || !staged.profiles.containsKey(from)) {
            return false;
        }
        // Rebuilt so the renamed profile keeps its place in the list.
        Map<String, Settings> renamed = new LinkedHashMap<>();
        staged.profiles.forEach((name, settings) -> renamed.put(name.equals(from) ? to : name, settings));
        staged.profiles = renamed;
        if (from.equals(staged.activeProfile)) {
            staged.activeProfile = to;
        }
        return true;
    }

    public boolean switchProfile(String name) {
        if (!staged.profiles.containsKey(name)) {
            return false;
        }
        staged.activeProfile = name;
        return true;
    }
}
