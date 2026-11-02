package dev.tierprofiles.surface;

import dev.tierprofiles.core.settings.Style;
import dev.tierprofiles.ui.Theme;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Draws a kit's icon at 16 by 16 on cards and in the settings. Default uses a real item,
 * so tints and 3D items such as shields come out right. Classic and Pixel use our icon
 * fonts, because the game's item renderer can't pick a different texture.
 *
 * Text surfaces can't hold an item, so nametags, tab and chat always use the fonts.
 */
public final class KitIcon {
    private static final int ITEM_FADE_CUTOFF = 128;

    private static final Map<String, Supplier<ItemStack>> ITEMS = Map.ofEntries(
            item("sword", Items.NETHERITE_SWORD),
            item("crystal", Items.END_CRYSTAL),
            item("axe", Items.DIAMOND_AXE),
            item("mace", Items.MACE),
            Map.entry("pot", () -> potion(Items.SPLASH_POTION, Potions.STRONG_HEALING)),
            item("neth_pot", Items.NETHERITE_CHESTPLATE),
            item("smp", Items.ENDER_PEARL),
            item("uhc", Items.GOLDEN_APPLE),
            item("spear", Items.NETHERITE_SPEAR),
            item("shield", Items.SHIELD),
            item("dia_smp", Items.DIAMOND_CHESTPLATE),
            item("dia_crystal", Items.END_CRYSTAL),
            item("og_vanilla", Items.GOLDEN_SWORD),
            item("minecart", Items.TNT_MINECART),
            item("trident", Items.TRIDENT),
            //? if >=26.1 {
            Map.entry("bed", () -> new ItemStack(Items.BED.pick(DyeColor.RED))),
            //?} else {
            /*Map.entry("bed", () -> new ItemStack(Items.RED_BED)),
            *///?}
            Map.entry("speed", () -> potion(Items.POTION, Potions.SWIFTNESS)),
            Map.entry("debuff", () -> potion(Items.SPLASH_POTION, Potions.POISON)),
            item("creeper", Items.CREEPER_SPAWN_EGG),
            item("early_game", Items.IRON_SWORD),
            item("late_game", Items.NETHERITE_CHESTPLATE),
            item("end_game", Items.END_CRYSTAL),
            item("elytra", Items.ELYTRA),
            item("bow", Items.BOW));

    private KitIcon() {
    }

    private static Map.Entry<String, Supplier<ItemStack>> item(String kit, Item item) {
        return Map.entry(kit, () -> new ItemStack(item));
    }

    private static ItemStack potion(Item bottle, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        ItemStack stack = new ItemStack(bottle);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }

    public static void draw(GuiGraphicsExtractor graphics, String kit, int x, int y, Style.IconSet set) {
        draw(graphics, kit, x, y, set, 1);
    }

    /** Items can't fade, so while fading they stay hidden until mostly solid. */
    public static void draw(GuiGraphicsExtractor graphics, String kit, int x, int y, Style.IconSet set, float alpha) {
        Supplier<ItemStack> stack = ITEMS.get(kit);
        if (set == Style.IconSet.DEFAULT && stack != null) {
            if (alpha * 255 >= ITEM_FADE_CUTOFF && dev.tierprofiles.ui.Ui.itemsReady()) {
                graphics.item(stack.get(), x, y);
            }
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(2, 2);
        graphics.text(Minecraft.getInstance().font, Icons.kit(kit, set), 0, 0, Theme.fade(0xFFFFFFFF, alpha), false);
        graphics.pose().popMatrix();
    }
}
