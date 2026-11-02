package dev.tierprofiles.ui;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Maps cosmetic preset ids to the game's real items. */
public final class Armor {
    private static final Item[][] PIECES = {
        {Items.AIR, Items.AIR, Items.AIR, Items.AIR},
        {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
        {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
        {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
        {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
        {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
        {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS},
    };

    private Armor() {
    }

    /**
     * @param material a preset id from Cosmetics.ARMOR
     * @param slot     0 helmet, 1 chestplate, 2 leggings, 3 boots
     */
    public static Item piece(String material, int slot) {
        int row = dev.tierprofiles.core.settings.Cosmetics.ARMOR.indexOf(material);
        return PIECES[Math.max(0, row)][slot];
    }

    /** The real smithing template for a trim pattern, or a barrier for none. */
    public static Item trimTemplate(String pattern) {
        return switch (pattern) {
            case "bolt" -> Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "coast" -> Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "dune" -> Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "eye" -> Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "flow" -> Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "host" -> Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "raiser" -> Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "rib" -> Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "sentry" -> Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "shaper" -> Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "silence" -> Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "snout" -> Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "spire" -> Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "tide" -> Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "vex" -> Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "ward" -> Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "wayfinder" -> Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "wild" -> Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE;
            default -> Items.BARRIER;
        };
    }

    public static Item trimItem(String material) {
        return switch (material) {
            case "copper" -> Items.COPPER_INGOT;
            case "diamond" -> Items.DIAMOND;
            case "emerald" -> Items.EMERALD;
            case "gold" -> Items.GOLD_INGOT;
            case "iron" -> Items.IRON_INGOT;
            case "lapis" -> Items.LAPIS_LAZULI;
            case "netherite" -> Items.NETHERITE_INGOT;
            case "quartz" -> Items.QUARTZ;
            case "redstone" -> Items.REDSTONE;
            default -> Items.AMETHYST_SHARD;
        };
    }
}
