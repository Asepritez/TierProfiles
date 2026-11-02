package dev.tierprofiles.core.config;

import dev.tierprofiles.core.settings.Settings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

/**
 * Share codes: your Settings as one line of text you can paste to a friend. The
 * format idea comes from Tallium's ShareCode (see THIRD_PARTY_NOTICES.txt).
 *
 * The format is PREFIX + compressed JSON in URL-safe base64 + "." + checksum. The
 * checksum exists to catch a partly copied code and give a helpful message.
 *
 * A code comes from another person, so nothing in it is trusted. Sizes are capped
 * so a tiny code can't inflate into gigabytes, and the result goes through
 * Settings.sanitize(). Only Settings can travel this way, so a code can't change
 * your server URL.
 */
public final class ShareCode {
    public static final String PREFIX = "TP1:";
    private static final int MAX_CODE_LENGTH = 64 * 1024;
    private static final int MAX_JSON_BYTES = 256 * 1024;

    private ShareCode() {
    }

    public static String encode(Settings settings) throws IOException {
        byte[] json = ConfigStore.GSON.toJson(settings).getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(compressed, new Deflater(Deflater.BEST_COMPRESSION))) {
            out.write(json);
        }
        byte[] data = compressed.toByteArray();

        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(data) + "." + checksum(data);
    }

    /** Returns sanitized settings, or throws an IOException with a message for the player. */
    public static Settings decode(String code) throws IOException {
        if (code == null || code.isBlank()) {
            throw new IOException("Paste a share code first.");
        }
        String text = code.trim();
        if (text.length() > MAX_CODE_LENGTH) {
            throw new IOException("That code is too long.");
        }
        if (!text.startsWith(PREFIX)) {
            throw new IOException("That isn't a TierProfiles code.");
        }

        String[] parts = text.substring(PREFIX.length()).split("\\.", -1);
        if (parts.length != 2) {
            throw new IOException("That code is incomplete.");
        }

        byte[] data;
        try {
            data = Base64.getUrlDecoder().decode(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new IOException("That code has invalid characters.");
        }
        if (!checksum(data).equalsIgnoreCase(parts[1])) {
            throw new IOException("That code is damaged. Copy the whole thing.");
        }

        String json = decodeUtf8(inflate(data));
        try {
            Settings settings = ConfigStore.GSON.fromJson(ConfigStore.withoutRemovedLists(json), Settings.class);
            if (settings == null) {
                throw new IOException("That code is empty.");
            }
            return settings.sanitize();
        } catch (RuntimeException e) {
            // A friend's code must never be able to crash the game, whatever is in it.
            throw new IOException("That code has invalid settings.");
        }
    }

    private static byte[] inflate(byte[] data) throws IOException {
        Inflater inflater = new Inflater();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try {
            inflater.setInput(data);
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && !inflater.finished()) {
                    throw new IOException("That code is incomplete.");
                }
                if (out.size() + count > MAX_JSON_BYTES) {
                    throw new IOException("That code is too large.");
                }
                out.write(buffer, 0, count);
            }
        } catch (DataFormatException e) {
            throw new IOException("That code is damaged. Copy the whole thing.");
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    private static String decodeUtf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("That code is damaged. Copy the whole thing.");
        }
    }

    private static String checksum(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return String.format(Locale.ROOT, "%08x", crc.getValue());
    }
}
