package dev.tierprofiles.ui;

/** The design tokens from the spec, as ARGB colours, so every screen matches. */
public final class Theme {
    public static final int BACKGROUND = 0xE6090B0C;
    public static final int PANEL = 0xEB191919;
    public static final int BORDER = 0xFF666666;
    public static final int ROW = 0xFF101010;
    public static final int ROW_ALT = 0xFF303030;
    public static final int SELECTED = 0xFF3E4A50;
    public static final int TEXT = 0xFFDDDDDD;
    public static final int MUTED = 0xFFAAAAAA;
    public static final int ACCENT = 0xFF68B5D5;
    public static final int GOOD = 0xFF77D877;
    public static final int BAD = 0xFFFF7777;
    public static final int BETA = 0xFFFFCF4A;

    private Theme() {
    }

    /** The same colour with a different opacity, from 0 to 1. */
    public static int withAlpha(int color, float alpha) {
        return Math.round(Math.clamp(alpha, 0, 1) * 255) << 24 | color & 0xFFFFFF;
    }

    /** Multiplies a colour's existing opacity, for fading something that's already see-through. */
    public static int fade(int color, float amount) {
        int alpha = Math.round((color >>> 24) * Math.clamp(amount, 0, 1));
        return alpha << 24 | color & 0xFFFFFF;
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }
}
