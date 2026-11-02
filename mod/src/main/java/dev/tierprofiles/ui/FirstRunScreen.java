package dev.tierprofiles.ui;

//? if >=26.1
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
    private static final int HEIGHT = 250;
    private static final int PADDING = 10;
    private static final int FACT_GAP = 4;
    private static final String README = "https://github.com/Asepritez/TierProfiles#privacy";

    private final Screen parent;
    private final Ui ui = new Ui();
    private boolean lookups = true;
    private boolean cardStyles = true;
    private float scale = 1;

    public FirstRunScreen(Screen parent) {
        super(Component.literal("Welcome to TierProfiles"));
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The game's own menu background, so the title screen's world shows around the notice.
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * A whole number of screen pixels per panel pixel, sized so the notice takes about
     * half the screen's height and the game stays visible around it.
     */
    private void measure() {
        int physicalWidth = minecraft.getWindow().getWidth();
        int physicalHeight = minecraft.getWindow().getHeight();
        int fits = Math.min((physicalWidth - 40) / WIDTH, (physicalHeight - 40) / HEIGHT);
        int comfortable = Math.round(physicalHeight / (HEIGHT * 2.2f));
        int pixels = Math.max(1, Math.min(fits, comfortable));
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
        factY = fact(left, factY, inner, Items.COMPASS, "Tiers: names and UUIDs of players around you",
                "They go straight to the five tier list websites, the same way their own pages ask.", null);
        factY = fact(left, factY, inner, Items.ARMOR_STAND, "Card styles: the same UUIDs go to the TierProfiles server",
                "So you see the backgrounds and armor other players picked for their cards.",
                "Your own style is sent only when you change it in Settings.");
        factY = fact(left, factY, inner, Items.SHIELD, "Your login stays with Minecraft",
                "We never see your account token. Saving uses Minecraft's usual join check.", null);
        factY = fact(left, factY, inner, Items.ENDER_PEARL, "We send nothing else",
                "No chat, no server address, no position. List logos come from each list's website.", null);

        int half = (inner - FACT_GAP) / 2;
        lookups = choice(left, factY, half, "Look up tiers online", "Off: no tiers, nothing sent.", lookups);
        // Styles need lookups, so they show as off while lookups are, but keep their own answer.
        boolean styles = choice(left + half + FACT_GAP, factY, inner - half - FACT_GAP, "Load card styles",
                "Off: every card is plain.", cardStyles && lookups);
        if (lookups) {
            cardStyles = styles;
        }

        int buttonY = y + HEIGHT - 28;
        ui.text("You can change this any time in Settings, More.", left, buttonY + 6, Theme.MUTED);
        if (ui.button(x + WIDTH - PADDING - 166, buttonY, 80, 20, "Read more", false)) {
            //? if >=26.1 {
            Blaze3D.openUri(URI.create(README));
            //?} else {
            /*net.minecraft.util.Util.getPlatform().openUri(URI.create(README));
            *///?}
        }
        if (ui.button(x + WIDTH - PADDING - 82, buttonY, 82, 20, "Sounds good", true)) {
            accept();
        }
        ui.end();
        graphics.pose().popMatrix();
    }

    /** One switch with its label, in a box that lights up green while it's on. */
    private boolean choice(int x, int y, int width, String title, String detail, boolean on) {
        ui.graphics.fill(x, y, x + width, y + 28, Theme.ROW);
        ui.graphics.outline(x, y, width, 28, on ? Theme.GOOD : Theme.BORDER);
        ui.text(title, x + 6, y + 5, 0xFFFFFFFF);
        ui.text(detail, x + 6, y + 16, Theme.MUTED);
        return ui.toggle(x + width - 62, y + 14, on);
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
        config.global.cardStyles = cardStyles;
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

    /**
     * Escape counts as an answer too, with the switch as it stands, so the notice is
     * never shown again after this.
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        accept();
    }
}
