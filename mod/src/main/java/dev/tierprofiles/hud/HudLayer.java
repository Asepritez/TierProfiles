package dev.tierprofiles.hud;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.input.Keys;
import dev.tierprofiles.input.TabSelection;
import dev.tierprofiles.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Everything of ours on the HUD. Drawn last, so it sits above the tab list. */
public final class HudLayer {
    private static final int HINT_GAP = 3;

    private HudLayer() {
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TierProfiles.MOD_ID, "cards"), HudLayer::draw);
    }

    private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        TabSelection.Extent tab = TabSelection.takeExtent();
        if (minecraft.gui.hud.isHidden() || minecraft.player == null) {
            return;
        }

        graphics.nextStratum();
        boolean tabShowing = TabSelection.isListVisible() && tab.bottom() > 0;
        if (CardState.isPreview() && !TabSelection.isListVisible()) {
            // Letting go of Tab puts a card you were only browsing away.
            CardState.close();
        }
        LookAtCard.draw(graphics, tabShowing ? TabSelection.selected() : null);

        int belowHints = tabShowing ? drawKeyHints(graphics, minecraft.font, tab.bottom()) : 0;
        if (CardState.isVisible()) {
            ProfileCard.draw(graphics, CardState.uuid(), CardState.name(), tabShowing ? tab.right() : 0, belowHints);
        }
    }

    private static String keyLabel(KeyMapping key, String arrow) {
        return key.isDefault() ? arrow : key.getTranslatedKeyMessage().getString();
    }

    /** Returns the y position just below the hints. */
    private static int drawKeyHints(GuiGraphicsExtractor graphics, Font font, int bottomOfTab) {
        String keys = keyLabel(Keys.UP, "\u2191") + keyLabel(Keys.DOWN, "\u2193")
                + keyLabel(Keys.LEFT, "\u2190") + keyLabel(Keys.RIGHT, "\u2192");
        String pin = Keys.PIN.getTranslatedKeyMessage().getString();
        String action = CardState.isPreview() ? " Keep card" : CardState.isOpen() ? " Close card" : " Open card";
        String text = keys + " Move   " + pin + action + "   " + Keys.ALL_RANKS.getTranslatedKeyMessage().getString() + " All ranks";

        int width = font.width(text) + 8;
        int x = (graphics.guiWidth() - width) / 2;
        int y = bottomOfTab + HINT_GAP;
        graphics.fill(x, y, x + width, y + 12, Theme.BACKGROUND);
        graphics.outline(x, y, width, 12, Theme.BORDER);
        graphics.text(font, text, x + 4, y + 2, Theme.MUTED);
        return y + 14;
    }
}
