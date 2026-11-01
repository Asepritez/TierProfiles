package dev.tierprofiles.core.logic;

/**
 * One piece of a formatted tag. The core can't draw, so it describes what to draw
 * and the mod turns these into Minecraft text and icons.
 *
 * @param text for TEXT, the words. For icons, the kit or list id.
 * @param rgb  0xRRGGBB
 */
public record Segment(Kind kind, String text, int rgb) {
    public enum Kind {
        TEXT, KIT_ICON, LIST_ICON
    }

    public static Segment text(String text, int rgb) {
        return new Segment(Kind.TEXT, text, rgb);
    }
}
