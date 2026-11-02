package dev.tierprofiles.ui;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.layout.CardPlacement;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.hud.CardState;
import dev.tierprofiles.hud.Portrait;
import dev.tierprofiles.hud.ProfileCard;
import dev.tierprofiles.surface.Decorator;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The permanent preview on the right of the settings screen. It shows staged settings,
 * so you see the result before you Apply, and it's built from the same code as the
 * real surfaces, so what you see is what you get.
 */
final class LiveStage {
    private static final Identifier BRICKS = Identifier.withDefaultNamespace("textures/block/stone_bricks.png");
    private static final Identifier GRASS = Identifier.withDefaultNamespace("textures/block/moss_block.png");
    private static final Identifier LOG = Identifier.withDefaultNamespace("textures/block/oak_log.png");
    private static final Identifier LEAVES = Identifier.withDefaultNamespace("textures/block/oak_leaves.png");
    private static final int TILE = 16;
    private static final int PADDING = 7;
    private static final int LABEL = 22;
    private static final int STRIP = 18;
    private static final int MODEL_SIZE = 56;
    private static final int SNAP_DISTANCE = 6;

    private final Object dragId = new Object();
    private int grabX;
    private int grabY;

    void draw(Ui ui, int x, int y, int width, int height, SettingsTab tab, ConfigFile config, float scale) {
        ui.panel(x, y, width, height);
        int inner = x + PADDING;
        int innerWidth = width - 2 * PADDING;

        String note = tab == SettingsTab.POSITION ? "Drag the card, or pick an anchor"
                : tab == SettingsTab.PROFILE ? "How others see your card" : "Updates as you change things";
        ui.text("Live Stage", inner, y + 8, Theme.ACCENT);
        ui.text(note, inner + ui.width("Live Stage "), y + 8, Theme.MUTED);

        Settings settings = config.active();
        Subject subject = subject();

        int cardHeight = ProfileCard.previewHeight(settings, config.global.cosmetics, subject.profile());
        int cardWidth = ProfileCard.previewWidth(settings);
        float cardScale = Math.min(1, (float) innerWidth / cardWidth);
        int scaledCardHeight = Math.round(cardHeight * cardScale);

        int cardY = y + height - scaledCardHeight - PADDING;
        int stripY = cardY - STRIP - 4;
        int sceneY = y + LABEL;
        int sceneHeight = stripY - sceneY - 4;

        if (tab == SettingsTab.POSITION) {
            drawPositionEditor(ui, inner, sceneY, innerWidth, sceneHeight, settings);
        } else {
            drawScene(ui, inner, sceneY, innerWidth, sceneHeight, settings, subject, scale);
        }
        drawStrip(ui, inner, stripY, innerWidth, settings, subject);

        ProfileCard.drawPreview(ui.graphics, settings, config.global.cosmetics, subject.profile(), subject.uuid(), subject.name(),
                inner + (innerWidth - Math.round(cardWidth * cardScale)) / 2, cardY, cardScale, scale);
    }

    private record Subject(UUID uuid, String name, PlayerProfile profile) {
    }

    /** The player on show: whoever was last pinned, then you, then a sample. */
    private static Subject subject() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID pinned = CardState.uuid();
        if (pinned != null) {
            PlayerProfile profile = TierProfiles.profiles().profile(pinned);
            if (profile != null) {
                return new Subject(pinned, CardState.name(), profile);
            }
        }
        UUID own = minecraft.getUser().getProfileId();
        PlayerProfile profile = own == null ? null : TierProfiles.profiles().profile(own);
        if (profile != null) {
            return new Subject(own, minecraft.getUser().getName(), profile);
        }
        PlayerProfile sample = SamplePlayers.showcasePlayer();
        return new Subject(sample.uuid(), sample.name(), sample);
    }

    /** A little scene built from the game's own block textures, with the model standing in it. */
    private static void drawScene(Ui ui, int x, int y, int width, int height, Settings settings, Subject subject, float scale) {
        ui.graphics.enableScissor(x, y, x + width, y + height);
        ui.graphics.fillGradient(x, y, x + width, y + height, 0xFF6D9BE0, 0xFFB7D0F2);

        int wallTop = y + height * 2 / 5;
        int groundTop = wallTop + 2 * TILE;
        for (int tree : new int[] {width / 6, width / 2 + 34, width - 60}) {
            drawTree(ui, x + tree, wallTop);
        }
        tile(ui, BRICKS, x, wallTop, width, 2 * TILE, 0xFFFFFFFF);
        tile(ui, GRASS, x, groundTop, width, y + height - groundTop, 0xFF9CC77A);
        ui.graphics.fill(x, wallTop, x + width, y + height, 0x30000000);

        int center = x + width / 2;
        int feet = y + height - 10;
        int modelTop = feet - MODEL_SIZE * 2;
        int left = Math.round((center - MODEL_SIZE) * scale);
        int right = Math.round((center + MODEL_SIZE) * scale);
        int size = Math.round(MODEL_SIZE * scale);
        Portrait.draw(ui.graphics, subject.uuid(), subject.name(), left, Math.round(modelTop * scale), right, Math.round(feet * scale), size, 0, null);

        Component tag = Decorator.preview(subject.profile(), Component.literal(subject.name()), Surface.NAMETAG, settings);
        int tagWidth = ui.font.width(tag);
        int tagX = center - tagWidth / 2;
        int tagY = modelTop - 10;
        ui.graphics.fill(tagX - 2, tagY - 1, tagX + tagWidth + 1, tagY + 9, 0x60000000);
        ui.text(tag, tagX, tagY, 0xFFFFFFFF);
        ui.graphics.disableScissor();
    }

    /** A tree behind the wall, built from the game's own log and leaf textures. */
    private static void drawTree(Ui ui, int centerX, int groundY) {
        int canopyWidth = 5 * TILE;
        int canopyTop = groundY - 4 * TILE;
        int left = centerX - canopyWidth / 2;
        ui.graphics.fill(left, canopyTop, left + canopyWidth, canopyTop + 2 * TILE, 0xFF2C5A22);
        ui.graphics.fill(left + TILE, canopyTop - TILE, left + canopyWidth - TILE, canopyTop, 0xFF2C5A22);
        tile(ui, LEAVES, left, canopyTop, canopyWidth, 2 * TILE, 0xFF5E9B44);
        tile(ui, LEAVES, left + TILE, canopyTop - TILE, canopyWidth - 2 * TILE, TILE, 0xFF5E9B44);
        tile(ui, LOG, centerX - TILE / 2, canopyTop + 2 * TILE, TILE, 2 * TILE, 0xFFFFFFFF);
    }

    private static void tile(Ui ui, Identifier texture, int x, int y, int width, int height, int color) {
        for (int row = 0; row < height; row += TILE) {
            for (int column = 0; column < width; column += TILE) {
                int tileWidth = Math.min(TILE, width - column);
                int tileHeight = Math.min(TILE, height - row);
                ui.graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + column, y + row, 0, 0, tileWidth, tileHeight, tileWidth, tileHeight, TILE, TILE, color);
            }
        }
    }

    /** One tab list row and one chat line. */
    private static void drawStrip(Ui ui, int x, int y, int width, Settings settings, Subject subject) {
        int half = width / 2 - 1;
        Component name = Component.literal(subject.name());
        ui.graphics.fill(x, y, x + half, y + STRIP, 0x40FFFFFF);
        ui.graphics.enableScissor(x, y, x + half, y + STRIP);
        ui.text(Decorator.preview(subject.profile(), name, Surface.TAB, settings), x + 4, y + 5, 0xFFFFFFFF);
        ui.graphics.disableScissor();

        int chatX = x + half + 2;
        ui.graphics.fill(chatX, y, x + width, y + STRIP, 0x60000000);
        ui.graphics.enableScissor(chatX, y, x + width, y + STRIP);
        Component chat = Decorator.previewChat(subject.profile(), Component.literal(subject.name() + ": gg"), settings);
        ui.text(chat, chatX + 4, y + 5, 0xFFFFFFFF);
        ui.graphics.disableScissor();
    }

    /**
     * A small copy of your screen with the card on it. Drag the card, and the zones it
     * has to stay clear of are marked.
     */
    private void drawPositionEditor(Ui ui, int x, int y, int width, int height, Settings settings) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        float mini = Math.min((float) width / screenWidth, (float) height / screenHeight);
        int miniWidth = Math.round(screenWidth * mini);
        int miniHeight = Math.round(screenHeight * mini);
        int left = x + (width - miniWidth) / 2;
        int top = y + (height - miniHeight) / 2;

        ui.graphics.fillGradient(left, top, left + miniWidth, top + miniHeight, 0xFF39506E, 0xFF23272B);
        ui.graphics.outline(left, top, miniWidth, miniHeight, Theme.BORDER);

        drawZone(ui, CardPlacement.crosshair(screenWidth, screenHeight), left, top, mini, 0x60FF7777);
        drawZone(ui, CardPlacement.hotbar(screenWidth, screenHeight), left, top, mini, 0x60FF7777);
        int tabWidth = Math.min(screenWidth - 20, 180);
        CardPlacement.Rect tabList = new CardPlacement.Rect((screenWidth - tabWidth) / 2, 10, tabWidth, screenHeight / 4);
        drawZone(ui, tabList, left, top, mini, 0x30FFCF4A);
        ui.text("Tab list", left + Math.round(tabList.x() * mini) + 3, top + Math.round((tabList.y() + tabList.height()) * mini) + 2, Theme.BETA);

        CardSettings card = settings.card;
        CardPlacement.Rect rect = ProfileCard.previewRect(settings, screenWidth, screenHeight);
        int cardX = left + Math.round(rect.x() * mini);
        int cardY = top + Math.round(rect.y() * mini);
        int cardWidth = Math.round(rect.width() * mini);
        int cardHeight = Math.round(rect.height() * mini);

        if (ui.clicked(cardX, cardY, cardWidth, cardHeight)) {
            ui.startDrag(dragId);
            grabX = ui.mouseX - cardX;
            grabY = ui.mouseY - cardY;
        }
        if (ui.isDragging(dragId)) {
            int wantedX = Math.round((ui.mouseX - grabX - left) / mini);
            int wantedY = Math.round((ui.mouseY - grabY - top) / mini);
            CardPlacement.Dragged dragged = CardPlacement.drag(wantedX, wantedY, rect.width(), rect.height(),
                    screenWidth, screenHeight, card.snap ? SNAP_DISTANCE : 0);
            card.anchor = dragged.anchor();
            card.offsetX = dragged.offsetX();
            card.offsetY = dragged.offsetY();
        }

        ui.graphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xE00C0E10);
        ui.graphics.outline(cardX, cardY, cardWidth, cardHeight, ui.isDragging(dragId) ? 0xFFFFFFFF : Theme.ACCENT);
        ui.graphics.centeredText(ui.font, "Card", cardX + cardWidth / 2, cardY + cardHeight / 2 - 4, 0xFFFFFFFF);
    }

    private static void drawZone(Ui ui, CardPlacement.Rect zone, int left, int top, float mini, int color) {
        int x = left + Math.round(zone.x() * mini);
        int y = top + Math.round(zone.y() * mini);
        ui.graphics.fill(x, y, x + Math.round(zone.width() * mini), y + Math.round(zone.height() * mini), color);
    }
}
