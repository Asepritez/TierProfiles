package dev.tierprofiles.core.settings;

/** Small helpers for 0xRRGGBB colours. */
public final class Colors {
    private Colors() {
    }

    public static int mix(int from, int to, float amount) {
        int red = mixChannel(from >> 16 & 0xFF, to >> 16 & 0xFF, amount);
        int green = mixChannel(from >> 8 & 0xFF, to >> 8 & 0xFF, amount);
        int blue = mixChannel(from & 0xFF, to & 0xFF, amount);
        return red << 16 | green << 8 | blue;
    }

    private static int mixChannel(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }

    public static int scale(int color, float factor) {
        return mix(0x000000, color, factor);
    }

    /** Returns null unless the text is six hex digits, with or without a #. */
    public static Integer parseHex(String text) {
        if (text == null) {
            return null;
        }
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        return Integer.parseInt(hex, 16);
    }

    public static String toHex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }
}
