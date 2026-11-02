package dev.tierprofiles.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Settings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/** Reads and writes the config file. */
public final class ConfigStore {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Lists that no longer exist. Saved settings and old share codes can still name them. */
    private static final Set<String> REMOVED_LISTS = Set.of("MCTIERS");

    private final Path path;

    public ConfigStore(Path path) {
        this.path = path;
    }

    /**
     * Never fails: a missing file gives defaults. A broken file is kept beside the
     * real one as .broken, so a typo can't silently destroy someone's settings.
     */
    public ConfigFile load() {
        if (!Files.exists(path)) {
            return new ConfigFile().sanitize();
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            ConfigFile config = GSON.fromJson(withoutRemovedLists(text), ConfigFile.class);
            if (config != null) {
                return config.sanitize();
            }
        } catch (IOException | RuntimeException e) {
            // Handled below, the same as an empty file.
        }
        keepBrokenCopy();
        return new ConfigFile().sanitize();
    }

    private void keepBrokenCopy() {
        try {
            Files.copy(path, path.resolveSibling(path.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Nothing more we can do, and the game should still start.
        }
    }

    /** Writes to a temporary file first, so a crash mid-save can't corrupt the config. */
    public void save(ConfigFile config) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Takes out every mention of a removed list before Gson sees it. Otherwise a rule
     * "on MCTiers" would read back as a rule on any list.
     */
    public static String withoutRemovedLists(String json) {
        JsonElement root = JsonParser.parseString(json);
        strip(root);
        return root.toString();
    }

    private static void strip(JsonElement element) {
        if (element instanceof JsonObject object) {
            for (String key : Set.copyOf(object.keySet())) {
                if (REMOVED_LISTS.contains(key)) {
                    object.remove(key);
                } else {
                    strip(object.get(key));
                }
            }
        } else if (element instanceof JsonArray array) {
            for (int i = array.size() - 1; i >= 0; i--) {
                if (isRemoved(array.get(i))) {
                    array.remove(i);
                } else {
                    strip(array.get(i));
                }
            }
        }
    }

    /** A removed list's name, or something that only applies to one, such as a rule on it. */
    private static boolean isRemoved(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return REMOVED_LISTS.contains(element.getAsString());
        }
        return element instanceof JsonObject object && object.get("list") != null && object.get("list").isJsonPrimitive()
                && REMOVED_LISTS.contains(object.get("list").getAsString());
    }

    public static ConfigFile copy(ConfigFile config) {
        return GSON.fromJson(GSON.toJson(config), ConfigFile.class).sanitize();
    }

    public static Settings copy(Settings settings) {
        return GSON.fromJson(GSON.toJson(settings), Settings.class).sanitize();
    }
}
