package dev.tierprofiles.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tierprofiles.core.config.ConfigEditor;
import dev.tierprofiles.core.config.ConfigStore;
import dev.tierprofiles.core.config.ShareCode;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Cosmetics;
import dev.tierprofiles.core.settings.HighlightRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CosmeticsTest {
    private static List<String> strings(JsonObject catalog, String key) {
        List<String> out = new ArrayList<>();
        JsonArray array = catalog.getAsJsonArray(key);
        array.forEach(element -> out.add(element.getAsString()));
        return out;
    }

    /** The server rejects anything off its list, so a mismatch would fail saves silently. */
    @Test
    void theModAndServerAgreeOnEveryPreset() throws IOException {
        Path file = Path.of("../../server/src/cosmetics.json");
        JsonObject catalog = JsonParser.parseString(Files.readString(file)).getAsJsonObject();

        assertEquals(Cosmetics.BACKGROUNDS, strings(catalog, "backgrounds"));
        assertEquals(Cosmetics.BORDERS, strings(catalog, "borders"));
        assertEquals(Cosmetics.ARMOR, strings(catalog, "armor"));
        assertEquals(Cosmetics.TRIM_PATTERNS, strings(catalog, "trimPatterns"));
        assertEquals(Cosmetics.TRIM_MATERIALS, strings(catalog, "trimMaterials"));
        assertEquals(Cosmetics.EFFECTS, strings(catalog, "effects"));

        List<Integer> accents = new ArrayList<>();
        catalog.getAsJsonArray("accents").forEach(element -> accents.add(element.getAsInt()));
        assertEquals(Cosmetics.ACCENTS, accents);
    }

    @Test
    void anythingOffTheListIsReplaced() {
        Cosmetics cosmetics = new Cosmetics();
        cosmetics.background = "<script>";
        cosmetics.border = null;
        cosmetics.helmet = "gold-plated-diamond";
        cosmetics.intensity = 5000;
        cosmetics.accent = 0x123456;
        cosmetics.sanitize();

        assertEquals("plain", cosmetics.background);
        assertEquals("thin", cosmetics.border);
        assertEquals("none", cosmetics.helmet);
        assertEquals(100, cosmetics.intensity);
        assertEquals(0x68B5D5, cosmetics.accent);
    }

    @Test
    void shareCodesCantCarryYourCardStyle() throws IOException {
        ConfigFile config = new ConfigFile().sanitize();
        config.global.cosmetics.background = "moon";
        assertEquals(false, ConfigStore.GSON.toJson(ShareCode.decode(ShareCode.encode(config.active()))).contains("moon"));
    }

    @Test
    void stagedChangesAreCounted() {
        ConfigEditor editor = new ConfigEditor(new ConfigFile().sanitize());
        assertEquals(0, editor.changeCount());

        editor.staged().active().style.longNames = true;
        assertEquals(1, editor.changeCount());

        editor.staged().active().card.overlayCloseSeconds = 40;
        assertEquals(2, editor.changeCount());

        editor.staged().active().style.longNames = false;
        assertEquals(1, editor.changeCount());
    }

    @Test
    void addingARuleCountsAsAChange() {
        ConfigEditor editor = new ConfigEditor(new ConfigFile().sanitize());
        editor.staged().active().rules.add(new HighlightRule());
        assertEquals(1, editor.changeCount());
    }
}
