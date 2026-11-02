package dev.tierprofiles.core.settings;

import java.util.List;

/**
 * How your own card looks to other mod users. Every value is a preset id from a fixed
 * list, with no free text, so there's nothing to moderate. The server checks the same
 * lists, kept in server/src/cosmetics.json, and a test keeps the two in step.
 *
 * All of this is drawn only inside TierProfiles' own GUI. Nothing here ever touches the
 * real player, the world or the Minecraft server.
 */
public final class Cosmetics {
    public static final List<String> BACKGROUNDS = List.of("plain", "moon", "starfield", "nebula", "embers", "snow");
    public static final List<String> BORDERS = List.of("thin", "double", "glow", "shimmer", "corners");
    public static final List<String> ARMOR = List.of("none", "leather", "chainmail", "iron", "golden", "diamond", "netherite");
    public static final List<String> TRIM_PATTERNS = List.of(
            "none", "bolt", "coast", "dune", "eye", "flow", "host", "raiser", "rib", "sentry", "shaper",
            "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild");
    public static final List<String> TRIM_MATERIALS = List.of(
            "amethyst", "copper", "diamond", "emerald", "gold", "iron", "lapis", "netherite", "quartz", "redstone");
    public static final List<String> EFFECTS = List.of("off", "sparkles", "motes", "both");
    public static final List<Integer> ACCENTS = List.of(
            0xFFE8B0, 0xFFCF4A, 0xFF8A1F, 0xFF7777, 0xFF5FA2, 0xC58BFF, 0x68B5D5, 0x77D877);

    public String background = "plain";
    public String border = "thin";
    public String helmet = "none";
    public String chestplate = "none";
    public String leggings = "none";
    public String boots = "none";
    public String trimPattern = "none";
    public String trimMaterial = "amethyst";
    public String effect = "off";
    /** How strong effects are, from 0 to 100. */
    public int intensity = 70;
    public int accent = 0x68B5D5;
    /** A kit to feature, or null. */
    public String featuredKit = null;
    /** A list id to feature, or null. */
    public String featuredList = null;

    private static String oneOf(List<String> allowed, String value) {
        return value != null && allowed.contains(value) ? value : allowed.getFirst();
    }

    public Cosmetics sanitize() {
        background = oneOf(BACKGROUNDS, background);
        border = oneOf(BORDERS, border);
        helmet = oneOf(ARMOR, helmet);
        chestplate = oneOf(ARMOR, chestplate);
        leggings = oneOf(ARMOR, leggings);
        boots = oneOf(ARMOR, boots);
        trimPattern = oneOf(TRIM_PATTERNS, trimPattern);
        trimMaterial = oneOf(TRIM_MATERIALS, trimMaterial);
        effect = oneOf(EFFECTS, effect);
        intensity = Math.clamp(intensity, 0, 100);
        if (!ACCENTS.contains(accent)) {
            accent = ACCENTS.get(6);
        }
        return this;
    }
}
