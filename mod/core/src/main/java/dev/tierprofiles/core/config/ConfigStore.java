package dev.tierprofiles.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Settings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads and writes the config file. */
public final class ConfigStore {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
            ConfigFile config = GSON.fromJson(text, ConfigFile.class);
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

    public static ConfigFile copy(ConfigFile config) {
        return GSON.fromJson(GSON.toJson(config), ConfigFile.class).sanitize();
    }

    public static Settings copy(Settings settings) {
        return GSON.fromJson(GSON.toJson(settings), Settings.class).sanitize();
    }
}
