package dev.tierprofiles.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tierprofiles.core.config.ConfigStore;
import dev.tierprofiles.core.config.ShareCode;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Global;
import dev.tierprofiles.core.settings.HighlightRule;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Style;
import dev.tierprofiles.core.settings.Surface;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShareCodeTest {
    /** Builds a correctly checksummed code around any text, as an attacker could. */
    private static String craft(byte[] plain) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(compressed)) {
            out.write(plain);
        }
        byte[] data = compressed.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(data);
        return ShareCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(data)
                + "." + String.format(Locale.ROOT, "%08x", crc.getValue());
    }

    private static String craft(String json) throws IOException {
        return craft(json.getBytes(StandardCharsets.UTF_8));
    }

    private static Settings sample() {
        Settings settings = new Settings().sanitize();
        settings.surface(Surface.NAMETAG).minimumTier = "LT3";
        settings.style.brackets = Style.Brackets.ROUND;
        settings.rules.add(HighlightRule.of(TierList.MCTIERS, "HT1", 0xAA00FF));
        return settings;
    }

    @Test
    void roundTrips() throws IOException {
        Settings back = ShareCode.decode(ShareCode.encode(sample()));
        assertEquals(ConfigStore.GSON.toJson(sample()), ConfigStore.GSON.toJson(back));
    }

    @Test
    void isOneShortUrlSafeLine() throws IOException {
        String code = ShareCode.encode(sample());
        assertTrue(code.startsWith("TP1:"));
        assertTrue(code.matches("TP1:[A-Za-z0-9_-]+\\.[0-9a-f]{8}"));
        assertTrue(code.length() < 2048, "code length " + code.length());
    }

    @Test
    void surroundingWhitespaceIsFine() throws IOException {
        assertEquals("LT3", ShareCode.decode("  " + ShareCode.encode(sample()) + "\n").surface(Surface.NAMETAG).minimumTier);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "hello", "TP1:", "TP1:abc", "TP1:abc.def", "TP2:abc.00000000", "TP1:!!!!.00000000"})
    void junkIsRefusedWithAMessage(String code) {
        IOException error = assertThrows(IOException.class, () -> ShareCode.decode(code));
        assertFalse(error.getMessage().isBlank());
    }

    @Test
    void nullIsRefused() {
        assertThrows(IOException.class, () -> ShareCode.decode(null));
    }

    @Test
    void aTruncatedCodeIsCaught() throws IOException {
        String code = ShareCode.encode(sample());
        assertThrows(IOException.class, () -> ShareCode.decode(code.substring(0, code.length() / 2)));
    }

    @Test
    void aChangedCharacterIsCaught() throws IOException {
        String code = ShareCode.encode(sample());
        int middle = code.length() / 2;
        char swapped = code.charAt(middle) == 'A' ? 'B' : 'A';
        String damaged = code.substring(0, middle) + swapped + code.substring(middle + 1);
        assertThrows(IOException.class, () -> ShareCode.decode(damaged));
    }

    @Test
    void anOversizedCodeIsRefused() {
        assertThrows(IOException.class, () -> ShareCode.decode("TP1:" + "A".repeat(70_000) + ".00000000"));
    }

    @Test
    void aZipBombIsRefused() throws IOException {
        byte[] huge = new byte[50 * 1024 * 1024];
        String code = craft(huge);
        assertTrue(code.length() < 100_000);
        assertThrows(IOException.class, () -> ShareCode.decode(code));
    }

    @Test
    void aValidCodeOfTheWrongShapeIsRefused() throws IOException {
        assertThrows(IOException.class, () -> ShareCode.decode(craft("[1,2,3]")));
        assertThrows(IOException.class, () -> ShareCode.decode(craft("null")));
        assertThrows(IOException.class, () -> ShareCode.decode(craft("{ nope")));
    }

    @Test
    void aCodeCantRedirectYourServer() throws IOException {
        String evil = """
                {"serverUrl": "https://evil.example", "global": {"serverUrl": "https://evil.example"},
                 "style": {"longNames": true}}""";
        Settings settings = ShareCode.decode(craft(evil));

        assertTrue(settings.style.longNames);
        assertFalse(ConfigStore.GSON.toJson(settings).contains("evil.example"));
        assertEquals("http://127.0.0.1:8787", new Global().serverUrl);
    }

    @Test
    void importedValuesAreSanitized() throws IOException {
        String wild = """
                {"card": {"overlayScale": 9999}, "surfaces": {"TAB": {"maxTags": -5, "minimumTier": "<script>"}}}""";
        Settings settings = ShareCode.decode(craft(wild));

        assertEquals(2.0f, settings.card.overlayScale);
        assertEquals(1, settings.surface(Surface.TAB).maxTags);
        assertEquals(null, settings.surface(Surface.TAB).minimumTier);
    }

    @Test
    void unknownListsInACodeAreDroppedRatherThanCrashing() throws IOException {
        String future = """
                {"filters": {"disabledLists": ["FUTURELIST", "MCPVP"], "listMinimums": {"FUTURELIST": "HT1", "MCTIERS": "LT3"}},
                 "style": {"listColors": {"FUTURELIST": {"HT1": 1}}}}""";
        Settings settings = ShareCode.decode(craft(future));

        assertEquals(java.util.Set.of(TierList.MCPVP), settings.filters.disabledLists);
        assertEquals(java.util.Map.of(TierList.MCTIERS, "LT3"), settings.filters.listMinimums);
    }

    @Test
    void aColourTooBigForAnIntIsAnErrorNotACrash() throws IOException {
        String code = craft("""
                {"style": {"tierColors": {"HT1": 99999999999}}}""");
        assertThrows(IOException.class, () -> ShareCode.decode(code));
    }
}
