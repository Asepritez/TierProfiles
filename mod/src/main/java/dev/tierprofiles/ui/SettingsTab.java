package dev.tierprofiles.ui;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum SettingsTab {
    TAGS("Tags", Items.NAME_TAG, "The tier tags beside names: above heads, in Tab, in chat and on the small card."),
    LISTS("Lists", Items.BOOKSHELF, "Remove whole tier lists or kits you never want to see, everywhere."),
    COLOURS("Colours", Items.FIRE_CORAL_FAN, "The colour of every tier, for all lists or each list on its own."),
    RULES("Rules", Items.COMPARATOR, "Recolour or mark players' names when they reach a tier, like red above HT3."),
    CARDS("Cards", Items.ITEM_FRAME, "How cards show: how much they say, what Tab and aiming show, animation and timing."),
    PROFILE("Profile", Items.ARMOR_STAND, "Style your own card: background, border, armor and trims. Others with the mod see it."),
    POSITION("Position", Items.COMPASS, "Where the big card sits on screen. Drag it on the Live Stage."),
    SHARING("Sharing", Items.WRITABLE_BOOK, "Save setups, swap between them, and share them with friends as a code."),
    KEYS("Keys", Items.TRIPWIRE_HOOK, "Change the keys for moving through Tab, pinning cards and All ranks."),
    MORE("More", Items.REDSTONE, "Lookups, motion, icon style, and what's happening behind the scenes.");

    private final String title;
    private final Item icon;
    private final String description;

    SettingsTab(String title, Item icon, String description) {
        this.title = title;
        this.icon = icon;
        this.description = description;
    }

    /** Shown when you hover the tab. */
    public String description() {
        return description;
    }

    public String title() {
        return title;
    }

    public Item icon() {
        return icon;
    }
}
