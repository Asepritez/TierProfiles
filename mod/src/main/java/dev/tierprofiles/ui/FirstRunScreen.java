package dev.tierprofiles.ui;

import com.mojang.blaze3d.Blaze3D;
import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.config.ConfigStore;
import dev.tierprofiles.core.settings.ConfigFile;
import java.net.URI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The privacy notice. No lookups happen until this has been seen. Like the settings it
 * picks its own whole-number scale, so it fits and stays sharp at any GUI scale.
 */
public final class FirstRunScreen extends Screen {
    private static final int WIDTH = 520;
    private static final int HEIGHT = 218;
    private static final int PADDING = 10;
    private static final int FACT_GAP = 4;
    private static final String README = "https://github.com/Asepritez/TierProfiles#privacy";

    private final Screen parent;
    private final Ui ui = new Ui();
    private boolean lookups = true;
    private float scale = 1;

    public FirstRunScreen(Screen parent) {
        super(Component.literal("Welcome to TierProfiles"));
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0000000);
    }

    /** The largest whole number of screen pixels per panel pixel that still fits. */
    private void measure() {
        int physicalWidth = minecraft.getWindow().getWidth();
        int physicalHeight = minecraft.getWindow().getHeight();
        int pixels = Math.max(1, Math.min((physicalWidth - 40) / WIDTH, (physicalHeight - 40) / HEIGHT));
        scale = (float) pixels / minecraft.getWindow().getGuiScale();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        measure();
        int virtualWidth = Math.round(width / scale);
        int virtualHeight = Math.round(height / scale);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        ui.begin(graphics, Math.round(mouseX / scale), Math.round(mouseY / scale));

        int x = (virtualWidth - WIDTH) / 2;
        int y = Math.max(2, (virtualHeight - HEIGHT) / 2);
        ui.panel(x, y, WIDTH, HEIGHT);

        int left = x + PADDING;
        int inner = WIDTH - 2 * PADDING;
        ui.item(Items.NAME_TAG, left, y + 8);
        ui.bigText("Welcome to TierProfiles", left + 22, y + 10, 1.5f, 0xFFFFFFFF);
        ui.text("To show tiers, we look up the players around you.", left, y + 32, Theme.TEXT);
        ui.text("Here is exactly what leaves your game.", left, y + 43, Theme.TEXT);

        int factY = y + 58;
        factY = fact(left, factY, inner, Items.COMPASS, "We send: names and UUIDs of players in your tab list",
                "They go to the TierProfiles server, which asks the tier lists.", null);
        factY = fact(left, factY, inner, Items.SHIELD, "Your login stays with Minecraft",
                "We never see your account token.", null);
        factY = fact(left, factY, inner, Items.ENDER_PEARL, "We send nothing else",
                "No chat, no server address, no position. Your card style is sent only when you save it.",
                "List logos are downloaded from each list's own website.");

        int rowY = factY;
        graphics.fill(left, rowY, left + inner, rowY + 28, Theme.ROW);
        graphics.outline(left, rowY, inner, 28, lookups ? Theme.GOOD : Theme.BORDER);
        ui.text("Look up tiers online", left + 6, rowY + 5, 0xFFFFFFFF);
        ui.text("Off means no data is sent and no tiers show.", left + 6, rowY + 16, Theme.MUTED);
        lookups = ui.toggle(left + inner - 62, rowY + 14, lookups);

        int buttonY = y + HEIGHT - 28;
        ui.text("You can change this any time in Settings, More.", left, buttonY + 6, Theme.MUTED);
        if (ui.button(x + WIDTH - PADDING - 166, buttonY, 80, 20, "Read more", false)) {
            Blaze3D.openUri(URI.create(README));
        }
        if (ui.button(x + WIDTH - PADDING - 82, buttonY, 82, 20, "Sounds good", true)) {
            accept();
        }
        ui.end();
        graphics.pose().popMatrix();
    }

    private int fact(int x, int y, int width, net.minecraft.world.item.Item icon, String title, String detail, String extra) {
        int height = extra == null ? 26 : 37;
        ui.graphics.fill(x, y, x + width, y + height, Theme.ROW);
        ui.graphics.outline(x, y, width, height, 0xFF2E3338);
        ui.item(icon, x + 5, y + (height - 16) / 2);
        ui.text(title, x + 26, y + 4, 0xFFFFFFFF);
        ui.text(detail, x + 26, y + 15, Theme.MUTED);
        if (extra != null) {
            ui.text(extra, x + 26, y + 26, Theme.MUTED);
        }
        return y + height + FACT_GAP;
    }

    private void accept() {
        ConfigFile config = ConfigStore.copy(TierProfiles.config());
        config.global.noticeSeen = true;
        config.global.lookupsEnabled = lookups;
        TierProfiles.publish(config);
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ui.onMousePressed(false);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        ui.onMouseReleased();
        return true;
    }

    /** It has to be answered, so Escape does nothing. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
