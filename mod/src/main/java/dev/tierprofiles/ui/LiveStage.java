package dev.tierprofiles.ui;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.layout.CardPlacement;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Cosmetics;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.hud.CardState;
import dev.tierprofiles.hud.Portrait;
import dev.tierprofiles.hud.ProfileCard;
import dev.tierprofiles.surface.Decorator;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The permanent preview on the right of the settings screen. It shows staged settings,
 * so you see the result before you Apply, and it's built from the same code as the
 * real surfaces, so what you see is what you get.
 */
final class LiveStage {
    /** A see-through grey, so the real game shows behind the preview. The screen leaves a hole for it. */
    static final int GLASS = 0x8C24282E;
    private static final int GLASS_TOP = 0x14FFFFFF;
    private static final int GLASS_BOTTOM = 0x00FFFFFF;
    private static final int FLOOR_SHADOW = 0x40000000;
    private static final int PADDING = 7;
    private static final int LABEL = 22;
    private static final int STRIP = 18;
    private static final int MODEL_SIZE = 56;
    private static final int SNAP_DISTANCE = 6;

    private final Object dragId = new Object();
    /** Someone picked in the Players tab, shown instead of you. Null shows you. */
    private Subject showing;
    private int grabX;
    private int grabY;

    void draw(Ui ui, int x, int y, int width, int height, SettingsTab tab, ConfigFile config, float scale) {
        ui.graphics.fill(x, y, x + width, y + height, GLASS);
        ui.graphics.outline(x, y, width, height, Theme.LINE);
        int inner = x + PADDING;
        int innerWidth = width - 2 * PADDING;

        Subject subject = tab == SettingsTab.PLAYERS && showing != null ? refreshed(showing) : subject();
        String note = tab == SettingsTab.POSITION ? "Drag the card, or pick an anchor"
                : tab == SettingsTab.PROFILE ? "How others see your card"
                : tab == SettingsTab.PLAYERS ? (showing != null ? "Their card" : "Pick a player to see their card")
                : "Updates as you change things";
        ui.text("Live Stage", inner, y + 8, Theme.ACCENT);
        ui.text(note, inner + ui.width("Live Stage "), y + 8, Theme.MUTED);

        Settings settings = config.active();
        // Your card shows your staged style; anyone else's shows the style they saved.
        Cosmetics look = subject == showing && showing != null ? TierProfiles.cosmeticsFor(subject.uuid()) : config.global.cosmetics;

        int cardHeight = ProfileCard.previewHeight(settings, look, subject.profile());
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

        ProfileCard.drawPreview(ui.graphics, settings, look, subject.profile(), subject.uuid(), subject.name(),
                inner + (innerWidth - Math.round(cardWidth * cardScale)) / 2, cardY, cardScale, scale);
    }

    record Subject(UUID uuid, String name, PlayerProfile profile) {
    }

    /** Shows this player on the stage, or you again with null. Their tiers are asked for first. */
    void show(UUID uuid, String name) {
        if (uuid == null) {
            showing = null;
            return;
        }
        TierProfiles.profiles().requestUrgent(uuid, name);
        if (dev.tierprofiles.core.net.ProfileService.isAllowed(TierProfiles.config().global)) {
            TierProfiles.cosmetics().request(uuid);
        }
        showing = new Subject(uuid, name, TierProfiles.profiles().profile(uuid));
    }

    UUID showing() {
        return showing == null ? null : showing.uuid();
    }

    /** The same player, with whatever has arrived since they were picked. */
    private Subject refreshed(Subject picked) {
        PlayerProfile now = TierProfiles.profiles().profile(picked.uuid());
        if (now != picked.profile()) {
            showing = new Subject(picked.uuid(), picked.name(), now);
        }
        return showing;
    }

    /**
     * Always you, because this is where you style your own card. Your tiers are asked
     * for when settings open; until they arrive the card says it's looking. Only an
     * offline account, which no list can know, gets a sample player instead.
     */
    static Subject subject() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID own = minecraft.getUser().getProfileId();
        String name = minecraft.getUser().getName();
        if (own == null || !dev.tierprofiles.core.net.ProfileService.isLookupEligible(own)) {
            PlayerProfile sample = SamplePlayers.showcasePlayer();
            return new Subject(sample.uuid(), sample.name(), sample);
        }
        TierProfiles.profiles().requestUrgent(own, name);
        return new Subject(own, name, TierProfiles.profiles().profile(own));
    }

    /** The model on a soft see-through grey, with the game itself visible behind. */
    private static void drawScene(Ui ui, int x, int y, int width, int height, Settings settings, Subject subject, float scale) {
        ui.graphics.enableScissor(x, y, x + width, y + height);
        ui.graphics.fillGradient(x, y, x + width, y + height, GLASS_TOP, GLASS_BOTTOM);

        int center = x + width / 2;
        int feet = y + height - 10;
        ui.graphics.fill(center - 22, feet - 1, center + 22, feet + 2, FLOOR_SHADOW);
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

        ui.graphics.fillGradient(left, top, left + miniWidth, top + miniHeight, 0x6039506E, 0x6023272B);
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
