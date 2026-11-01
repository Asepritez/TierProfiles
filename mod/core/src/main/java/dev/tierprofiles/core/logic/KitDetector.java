package dev.tierprofiles.core.logic;

import java.util.Set;

/**
 * Guesses the current gamemode from item ids in your own inventory, for the AUTO
 * kit mode. This only reads your inventory, so it reveals nothing about anyone.
 * Rules go from most to least distinctive, and null means no confident guess.
 */
public final class KitDetector {
    private KitDetector() {
    }

    public static String detect(Set<String> items) {
        if (items.contains("minecraft:mace")) {
            return "mace";
        }
        if (items.contains("minecraft:end_crystal")) {
            return "crystal";
        }
        if (items.contains("minecraft:trident")) {
            return "trident";
        }
        if (items.contains("minecraft:splash_potion")) {
            return items.contains("minecraft:netherite_chestplate") ? "neth_pot" : "pot";
        }
        if (items.contains("minecraft:lava_bucket") && items.contains("minecraft:golden_apple")) {
            return "uhc";
        }
        if (items.contains("minecraft:diamond_axe") || items.contains("minecraft:netherite_axe")) {
            return "axe";
        }
        if (items.contains("minecraft:diamond_sword") || items.contains("minecraft:netherite_sword")) {
            return "sword";
        }
        return null;
    }
}
