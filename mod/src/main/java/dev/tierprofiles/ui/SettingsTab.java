package dev.tierprofiles.ui;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum SettingsTab {
    TAGS("Tags", Items.NETHERITE_SWORD),
    COLOURS("Colours", Items.SPLASH_POTION),
    RULES("Rules", Items.NETHER_STAR),
    PROFILE("Profile", Items.NETHERITE_CHESTPLATE),
    SHARING("Sharing", Items.WRITABLE_BOOK),
    KEYS("Keys", Items.TRIPWIRE_HOOK),
    MORE("More", Items.COMPARATOR);

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
