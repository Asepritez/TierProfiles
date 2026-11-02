package dev.tierprofiles.core.settings;

import dev.tierprofiles.core.Maths;
import dev.tierprofiles.core.model.TierList;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    /** The trim older versions put on every piece. Kept so their saved cards still load. */
    public String trimPattern = "none";
    public String trimMaterial = "amethyst";
    /** Each piece's own trim: helmet, chestplate, leggings, boots. Null means use the old shared one. */
    public String helmetTrim, chestplateTrim, leggingsTrim, bootsTrim;
    public String helmetTrimMaterial, chestplateTrimMaterial, leggingsTrimMaterial, bootsTrimMaterial;
    public String effect = "off";
    /** How strong effects are, from 0 to 100. */
    public int intensity = 70;
    public int accent = 0x68B5D5;
    /** A kit to feature, or null. */
    public String featuredKit = null;
    /** A list id to feature, or null. */
    public String featuredList = null;
    /** Lists left off your card, by id, for everyone who sees it. */
    public List<String> hiddenLists = new ArrayList<>();
    /** Kits left off your card, for everyone who sees it. */
    public List<String> hiddenKits = new ArrayList<>();

    public static final int MAX_HIDDEN_KITS = 32;

    private static String oneOf(List<String> allowed, String value) {
        return value != null && allowed.contains(value) ? value : allowed.get(0);
    }

    /** @param slot 0 helmet, 1 chestplate, 2 leggings, 3 boots */
    public String trim(int slot) {
        return switch (slot) {
            case 0 -> helmetTrim;
            case 1 -> chestplateTrim;
            case 2 -> leggingsTrim;
            default -> bootsTrim;
        };
    }

    public String trimMaterial(int slot) {
        return switch (slot) {
            case 0 -> helmetTrimMaterial;
            case 1 -> chestplateTrimMaterial;
            case 2 -> leggingsTrimMaterial;
            default -> bootsTrimMaterial;
        };
    }

    public void setTrim(int slot, String pattern) {
        switch (slot) {
            case 0 -> helmetTrim = pattern;
            case 1 -> chestplateTrim = pattern;
            case 2 -> leggingsTrim = pattern;
            default -> bootsTrim = pattern;
        }
        syncSharedTrim();
    }

    public void setTrimMaterial(int slot, String material) {
        switch (slot) {
            case 0 -> helmetTrimMaterial = material;
            case 1 -> chestplateTrimMaterial = material;
            case 2 -> leggingsTrimMaterial = material;
            default -> bootsTrimMaterial = material;
        }
        syncSharedTrim();
    }

    /** Older clients only read the shared trim, so it follows the chestplate. */
    private void syncSharedTrim() {
        trimPattern = chestplateTrim;
        trimMaterial = chestplateTrimMaterial;
    }

    public String armor(int slot) {
        return switch (slot) {
            case 0 -> helmet;
            case 1 -> chestplate;
            case 2 -> leggings;
            default -> boots;
        };
    }

    public void setArmor(int slot, String material) {
        switch (slot) {
            case 0 -> helmet = material;
            case 1 -> chestplate = material;
            case 2 -> leggings = material;
            default -> boots = material;
        }
    }

    public Set<TierList> hiddenListSet() {
        Set<TierList> lists = EnumSet.noneOf(TierList.class);
        for (String id : hiddenLists) {
            TierList list = TierList.byId(id);
            if (list != null) {
                lists.add(list);
            }
        }
        return lists;
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
        helmetTrim = oneOf(TRIM_PATTERNS, helmetTrim == null ? trimPattern : helmetTrim);
        chestplateTrim = oneOf(TRIM_PATTERNS, chestplateTrim == null ? trimPattern : chestplateTrim);
        leggingsTrim = oneOf(TRIM_PATTERNS, leggingsTrim == null ? trimPattern : leggingsTrim);
        bootsTrim = oneOf(TRIM_PATTERNS, bootsTrim == null ? trimPattern : bootsTrim);
        helmetTrimMaterial = oneOf(TRIM_MATERIALS, helmetTrimMaterial == null ? trimMaterial : helmetTrimMaterial);
        chestplateTrimMaterial = oneOf(TRIM_MATERIALS, chestplateTrimMaterial == null ? trimMaterial : chestplateTrimMaterial);
        leggingsTrimMaterial = oneOf(TRIM_MATERIALS, leggingsTrimMaterial == null ? trimMaterial : leggingsTrimMaterial);
        bootsTrimMaterial = oneOf(TRIM_MATERIALS, bootsTrimMaterial == null ? trimMaterial : bootsTrimMaterial);
        syncSharedTrim();
        effect = oneOf(EFFECTS, effect);

        Set<String> lists = new LinkedHashSet<>();
        if (hiddenLists != null) {
            for (String id : hiddenLists) {
                if (TierList.byId(id) != null) {
                    lists.add(id);
                }
            }
        }
        hiddenLists = new ArrayList<>(lists);
        Set<String> kits = new LinkedHashSet<>();
        if (hiddenKits != null) {
            for (String kit : hiddenKits) {
                if (Filters.isKitId(kit) && kits.size() < MAX_HIDDEN_KITS) {
                    kits.add(kit);
                }
            }
        }
        hiddenKits = new ArrayList<>(kits);
        intensity = Maths.clamp(intensity, 0, 100);
        if (!ACCENTS.contains(accent)) {
            accent = ACCENTS.get(6);
        }
        return this;
    }
}
