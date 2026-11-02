package dev.tierprofiles.ui;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum SettingsTab {
    TAGS("Tags", Items.NAME_TAG),
    COLOURS("Colours", Items.FIRE_CORAL_FAN),
    RULES("Rules", Items.LEVER),
    PROFILE("Profile", Items.ARMOR_STAND),
    POSITION("Position", Items.COMPASS),
    SHARING("Sharing", Items.WRITABLE_BOOK),
    KEYS("Keys", Items.TRIPWIRE_HOOK),
    MORE("More", Items.REDSTONE);

    private final String title;
    private final Item icon;

    SettingsTab(String title, Item icon) {
        this.title = title;
        this.icon = icon;
    }

    public String title() {
        return title;
    }

    public Item icon() {
        return icon;
    }
}
