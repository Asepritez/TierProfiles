package dev.tierprofiles.hud;

import dev.tierprofiles.core.settings.Cosmetics;
import dev.tierprofiles.ui.Armor;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

/**
 * Puts card armor on the stand-in model. These are real ItemStacks with real trim
 * components, so they look exactly right, but they only ever exist on the Portrait,
 * which is never in the world. Nothing here touches the real player.
 */
final class Wardrobe {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Wardrobe() {
    }

    static void dress(RemotePlayer model, Cosmetics look) {
        if (!dev.tierprofiles.ui.Ui.itemsReady()) {
            return;
        }
        String[] pieces = look == null ? new String[] {"none", "none", "none", "none"}
                : new String[] {look.helmet, look.chestplate, look.leggings, look.boots};
        for (int slot = 0; slot < SLOTS.length; slot++) {
            ItemStack stack = new ItemStack(Armor.piece(pieces[slot], slot));
            if (look != null && !stack.isEmpty()) {
                addTrim(stack, look);
            }
            model.setItemSlot(SLOTS[slot], stack);
        }
    }

    private static void addTrim(ItemStack stack, Cosmetics look) {
        if (look.trimPattern.equals("none")) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        HolderLookup.Provider registries = minecraft.level.registryAccess();
        Holder<TrimPattern> pattern = registries.lookupOrThrow(Registries.TRIM_PATTERN)
                .get(ResourceKey.create(Registries.TRIM_PATTERN, Identifier.withDefaultNamespace(look.trimPattern.toLowerCase(Locale.ROOT))))
                .map(holder -> (Holder<TrimPattern>) holder)
                .orElse(null);
        Holder<TrimMaterial> material = registries.lookupOrThrow(Registries.TRIM_MATERIAL)
                .get(ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.withDefaultNamespace(look.trimMaterial.toLowerCase(Locale.ROOT))))
                .map(holder -> (Holder<TrimMaterial>) holder)
                .orElse(null);
        if (Objects.nonNull(pattern) && Objects.nonNull(material)) {
            stack.set(DataComponents.TRIM, new ArmorTrim(material, pattern));
        }
    }
}
