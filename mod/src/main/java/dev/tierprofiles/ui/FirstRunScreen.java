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


/** The privacy notice. No lookups happen until this has been seen. */
public final class FirstRunScreen extends Screen {
    private static final int WIDTH = 330;
    private static final int HEIGHT = 196;
    private static final String README = "https://github.com/Asepritez/TierProfiles#privacy";

    private final Screen parent;
    private final Ui ui = new Ui();
    private boolean lookups = true;

    public FirstRunScreen(Screen parent) {
        super(Component.literal("Welcome to TierProfiles"));
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ui.begin(graphics, mouseX, mouseY);
        int x = (width - WIDTH) / 2;
        int y = Math.max(2, (height - HEIGHT) / 2);
        ui.panel(x, y, WIDTH, HEIGHT);

        int left = x + 8;
        int top = y + 8;
        ui.text(Component.literal("Welcome to TierProfiles").withStyle(s -> s.withBold(true)), left, top, 0xFFFFFFFF);
        ui.text("To show tiers, we look up the players around you.", left, top + 14, Theme.TEXT);
        ui.text("Here is exactly what leaves your game.", left, top + 24, Theme.TEXT);

        fact(left, top + 37, "We send: names and UUIDs of players in your tab list", "They go to the TierProfiles server, which asks the tier lists.", null);
        fact(left, top + 63, "Your login stays with Minecraft", "We never see your account token.", null);
        fact(left, top + 89, "We send nothing else", "No chat, no server address, no position.", "Your card style is sent only when you save it. Logos come from each list's site.");

        int rowY = top + 125;
        graphics.fill(left, rowY, left + WIDTH - 16, rowY + 24, Theme.ROW);
        graphics.outline(left, rowY, WIDTH - 16, 24, lookups ? Theme.GOOD : Theme.BORDER);
        ui.text("Look up tiers online", left + 5, rowY + 4, 0xFFFFFFFF);
        ui.text("Off means no data is sent and no tiers show.", left + 5, rowY + 14, Theme.MUTED);
        lookups = ui.toggle(left + WIDTH - 66, rowY + 12, lookups);

        int buttonY = y + HEIGHT - 22;
        if (ui.button(x + WIDTH - 158, buttonY, 72, 16, "Read more", false)) {
            Blaze3D.openUri(URI.create(README));
        }
        if (ui.button(x + WIDTH - 80, buttonY, 72, 16, "Sounds good", true)) {
            accept();
        }
        ui.text("Change this any time in Settings, More.", left, buttonY + 4, Theme.MUTED);
        ui.end();
    }

    private void fact(int x, int y, String title, String detail, String extra) {
        int height = extra == null ? 24 : 34;
        ui.graphics.fill(x, y, x + WIDTH - 16, y + height, Theme.ROW);
        ui.graphics.outline(x, y, WIDTH - 16, height, 0xFF333333);
        ui.text(title, x + 5, y + 4, 0xFFFFFFFF);
        ui.text(detail, x + 5, y + 14, Theme.MUTED);
        if (extra != null) {
            ui.text(extra, x + 5, y + 24, Theme.MUTED);
        }
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
