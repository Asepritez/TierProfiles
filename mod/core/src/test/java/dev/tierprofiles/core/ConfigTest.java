package dev.tierprofiles.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tierprofiles.core.config.ConfigEditor;
import dev.tierprofiles.core.config.ConfigStore;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Global;
import dev.tierprofiles.core.settings.HighlightRule;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Style;
import dev.tierprofiles.core.settings.Surface;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {
    private static ConfigFile fresh() {
        return new ConfigFile().sanitize();
    }

    @Test
    void stagedChangesDontTouchLiveUntilApply() {
        ConfigFile live = fresh();
        ConfigEditor editor = new ConfigEditor(live);
        editor.staged().active().style.longNames = true;

        assertFalse(live.active().style.longNames);
        assertTrue(editor.isDirty());

        ConfigFile applied = editor.apply();
        assertTrue(applied.active().style.longNames);
        assertFalse(editor.isDirty());
    }

    @Test
    void applyPublishesANewObjectSoTheRenderThreadNeverSeesAHalfChange() {
        ConfigFile live = fresh();
        ConfigEditor editor = new ConfigEditor(live);
        editor.staged().active().style.longNames = true;
        ConfigFile applied = editor.apply();

        assertNotSame(live, applied);
        assertFalse(live.active().style.longNames);

        editor.staged().active().style.perListColors = true;
        assertFalse(applied.active().style.perListColors);
    }

    @Test
    void discardThrowsChangesAway() {
        ConfigEditor editor = new ConfigEditor(fresh());
        editor.staged().active().style.longNames = true;
        editor.discard();

        assertFalse(editor.staged().active().style.longNames);
        assertFalse(editor.isDirty());
    }

    @Test
    void aNewEditorStartsClean() {
        assertFalse(new ConfigEditor(fresh()).isDirty());
    }

    @Test
    void undoingAChangeByHandIsNotDirty() {
        ConfigEditor editor = new ConfigEditor(fresh());
        editor.staged().active().style.longNames = true;
        editor.staged().active().style.longNames = false;
        assertFalse(editor.isDirty());
    }

    @Test
    void namedProfilesAreIndependent() {
        ConfigEditor editor = new ConfigEditor(fresh());
        assertTrue(editor.createProfile("Tournament", editor.staged().active()));
        assertTrue(editor.switchProfile("Tournament"));
        editor.staged().active().style.longNames = true;

        assertFalse(editor.staged().profiles.get("Default").style.longNames);
        assertTrue(editor.staged().profiles.get("Tournament").style.longNames);
    }

    @Test
    void profileChangesAreStagedToo() {
        ConfigFile live = fresh();
        ConfigEditor editor = new ConfigEditor(live);
        editor.createProfile("Casual", editor.staged().active());
        assertEquals(1, live.profiles.size());
        editor.discard();
        assertEquals(1, editor.staged().profiles.size());
    }

    @Test
    void duplicateOrBlankProfileNamesAreRefused() {
        ConfigEditor editor = new ConfigEditor(fresh());
        assertFalse(editor.createProfile("Default", editor.staged().active()));
        assertFalse(editor.createProfile("  ", editor.staged().active()));
        assertFalse(editor.createProfile("x".repeat(33), editor.staged().active()));
    }

    @Test
    void theLastProfileCantBeDeleted() {
        ConfigEditor editor = new ConfigEditor(fresh());
        assertFalse(editor.deleteProfile("Default"));
    }

    @Test
    void deletingTheActiveProfileActivatesAnother() {
        ConfigEditor editor = new ConfigEditor(fresh());
        editor.createProfile("Casual", editor.staged().active());
        editor.switchProfile("Casual");
        assertTrue(editor.deleteProfile("Casual"));
        assertEquals("Default", editor.staged().activeProfile);
    }

    @Test
    void renamingKeepsPositionAndActiveProfile() {
        ConfigEditor editor = new ConfigEditor(fresh());
        editor.createProfile("Casual", editor.staged().active());
        assertTrue(editor.renameProfile("Default", "Main"));
        assertEquals(List.of("Main", "Casual"), new ArrayList<>(editor.staged().profiles.keySet()));
        assertEquals("Main", editor.staged().activeProfile);
    }

    @Test
    void savesAndLoadsEverything(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("nested/tierprofiles.json");
        ConfigFile config = fresh();
        config.global.lookupsEnabled = false;
        Settings settings = config.active();
        settings.surface(Surface.NAMETAG).minimumTier = "LT3";
        settings.filters.listPriority = new ArrayList<>(List.of(TierList.SUBTIERS, TierList.MCTIERS));
        settings.filters.listMinimums.put(TierList.MCPVP, "HT2");
        settings.filters.disabledLists.add(TierList.PVPHQ);
        settings.style.brackets = Style.Brackets.ANGLE;
        settings.style.listColors.put(TierList.MCPVP, new java.util.LinkedHashMap<>(java.util.Map.of("HT1", 0x112233)));
        settings.rules.add(HighlightRule.of(TierList.MCTIERS, "HT1", 0xAA00FF));

        new ConfigStore(file).save(config);
        ConfigFile loaded = new ConfigStore(file).load();

        assertFalse(loaded.global.lookupsEnabled);
        Settings back = loaded.active();
        assertEquals("LT3", back.surface(Surface.NAMETAG).minimumTier);
        assertEquals(TierList.SUBTIERS, back.filters.listPriority.getFirst());
        assertEquals("HT2", back.filters.listMinimums.get(TierList.MCPVP));
        assertTrue(back.filters.disabledLists.contains(TierList.PVPHQ));
        assertEquals(Style.Brackets.ANGLE, back.style.brackets);
        assertEquals(0x112233, back.style.listColors.get(TierList.MCPVP).get("HT1"));
        assertEquals(0xAA00FF, back.rules.getFirst().color);
    }

    @Test
    void aMissingFileGivesDefaults(@TempDir Path folder) {
        ConfigFile loaded = new ConfigStore(folder.resolve("nope.json")).load();
        assertEquals("Default", loaded.activeProfile);
        assertTrue(loaded.global.lookupsEnabled);
    }

    @Test
    void aBrokenFileGivesDefaultsAndKeepsACopy(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("tierprofiles.json");
        Files.writeString(file, "{ this is not json");
        ConfigFile loaded = new ConfigStore(file).load();

        assertEquals("Default", loaded.activeProfile);
        assertEquals("{ this is not json", Files.readString(folder.resolve("tierprofiles.json.broken")));
    }

    @Test
    void unknownFieldsAndEnumValuesDontBreakLoading(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("tierprofiles.json");
        Files.writeString(file, """
                {"futureField": 1, "activeProfile": "A", "profiles": {"A": {
                  "style": {"brackets": "HEXAGON"},
                  "filters": {"listPriority": ["FUTURELIST", "MCPVP"], "kitMode": "PSYCHIC"},
                  "surfaces": {"HOLOGRAM": {"enabled": true}, "TAB": {"maxTags": 99, "minimumTier": "ZZ9"}}
                }}}""");
        Settings settings = new ConfigStore(file).load().active();

        assertEquals(Style.Brackets.SQUARE, settings.style.brackets);
        assertEquals(TierList.MCPVP, settings.filters.listPriority.getFirst());
        assertEquals(TierList.values().length, settings.filters.listPriority.size());
        assertEquals(5, settings.surface(Surface.TAB).maxTags);
        assertNull(settings.surface(Surface.TAB).minimumTier);
    }

    @Test
    void numbersAreClamped() {
        Settings settings = new Settings();
        settings.card.overlayScale = 50f;
        settings.card.lookAtOpacity = -3f;
        settings.card.overlayOpacity = Float.NaN;
        settings.card.overlayCloseSeconds = -1;
        settings.sanitize();

        assertEquals(CardSettings.MAX_SCALE, settings.card.overlayScale);
        assertEquals(0f, settings.card.lookAtOpacity);
        assertEquals(0.9f, settings.card.overlayOpacity);
        assertEquals(0, settings.card.overlayCloseSeconds);
    }

    @Test
    void ruleCountIsCapped() {
        Settings settings = new Settings();
        for (int i = 0; i < 100; i++) {
            settings.rules.add(new HighlightRule());
        }
        assertEquals(Settings.MAX_RULES, settings.sanitize().rules.size());
    }

    @Test
    void invalidTierInARuleIsRepaired() {
        HighlightRule rule = new HighlightRule();
        rule.minimumTier = "nonsense";
        Settings settings = new Settings();
        settings.rules.add(rule);
        assertEquals("HT3", settings.sanitize().rules.getFirst().minimumTier);
    }

    @Test
    void badColoursAndTierKeysAreDropped() {
        Settings settings = new Settings();
        settings.style.tierColors.put("banana", 0xFFFFFF);
        settings.style.tierColors.put("HT1", 0xFF123456);
        settings.sanitize();
        assertEquals(java.util.Map.of("HT1", 0x123456), settings.style.tierColors);
    }

    @Test
    void serverUrlMustBeWeb() {
        assertTrue(Global.isValidServerUrl("http://127.0.0.1:8787"));
        assertTrue(Global.isValidServerUrl("https://tierprofiles.example.workers.dev"));
        assertFalse(Global.isValidServerUrl("file:///etc/passwd"));
        assertFalse(Global.isValidServerUrl("ftp://example.com"));
        assertFalse(Global.isValidServerUrl("not a url"));
        assertFalse(Global.isValidServerUrl(null));
    }

    @Test
    void aBadServerUrlFallsBackAndTrailingSlashesGo() {
        ConfigFile config = fresh();
        config.global.serverUrl = "javascript:alert(1)";
        assertEquals(Global.DEFAULT_SERVER_URL, config.sanitize().global.serverUrl);

        config.global.serverUrl = "https://example.com/";
        assertEquals("https://example.com", config.sanitize().global.serverUrl);
    }

    @Test
    void anActiveProfileThatDoesntExistIsRepaired() {
        ConfigFile config = fresh();
        config.activeProfile = "Ghost";
        assertEquals("Default", config.sanitize().activeProfile);
    }
}
