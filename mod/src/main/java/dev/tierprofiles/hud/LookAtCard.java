package dev.tierprofiles.hud;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.anim.Easing;
import dev.tierprofiles.core.anim.Reveal;
import dev.tierprofiles.core.logic.Tag;
import dev.tierprofiles.core.logic.TagFormatter;
import dev.tierprofiles.core.logic.TagSelector;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.core.settings.SurfaceSettings;
import dev.tierprofiles.surface.Decorator;
import dev.tierprofiles.surface.TagText;
import dev.tierprofiles.ui.Theme;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** A small card when you aim at a player. It fades in and out rather than popping. */
public final class LookAtCard {
    private static final int PADDING = 4;
    private static final int HEIGHT = 15;
    private static final int BELOW_CROSSHAIR = 12;
    private static final int EDGE = 4;
    private static final long FADE_MILLIS = 140;

    private static final Reveal reveal = new Reveal();
    private static UUID target;
    private static long targetSince;
    private static Component shownName;
    private static Component shownTags;

    private LookAtCard() {
    }

    public static void draw(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Settings settings = TierProfiles.settings();
        long now = CardState.now();

        Component tags = settings.surface(Surface.LOOK_AT).enabled ? tagsForAimedPlayer(minecraft, settings, now) : null;
        boolean wanted = tags != null;
        if (wanted) {
            shownTags = tags;
        }
        if (wanted != reveal.isOpen()) {
            boolean animate = settings.card.effectiveStyle(TierProfiles.config().global.reduceMotion)
                    != CardSettings.AnimationStyle.OFF;
            reveal.set(wanted, now, animate ? settings.card.scaled(FADE_MILLIS) : 0, Easing.OUT_CUBIC);
        }

        if (shownTags != null && reveal.isVisible(now)) {
            drawPanel(graphics, minecraft.font, shownName, shownTags, settings.card, reveal.position(now));
        }
    }

    private static Component tagsForAimedPlayer(Minecraft minecraft, Settings settings, long now) {
        Player player = aimedPlayer(minecraft);
        if (player == null) {
            target = null;
            return null;
        }
        if (!player.getUUID().equals(target)) {
            target = player.getUUID();
            targetSince = now;
        }
        if (now - targetSince < settings.card.lookAtDelayMs) {
            return null;
        }

        PlayerProfile profile = TierProfiles.profiles().profile(player.getUUID());
        List<Tag> tags = TagSelector.select(profile, Surface.LOOK_AT, settings, Decorator.context(player.getUUID()));
        if (tags.isEmpty()) {
            return null;
        }
        shownName = player.getName();
        SurfaceSettings surface = settings.surface(Surface.LOOK_AT);
        return TagText.build(TagFormatter.format(tags, surface, settings.style), settings.style);
    }

    /** Invisible players can be aimed at, so they're skipped, or the card would reveal them. */
    private static Player aimedPlayer(Minecraft minecraft) {
        Entity entity = minecraft.crosshairPickEntity;
        if (entity instanceof Player player && minecraft.player != null && !player.isInvisibleTo(minecraft.player)) {
            return player;
        }
        return null;
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, Font font, Component name, Component tags, CardSettings card, float alpha) {
        int width = PADDING * 2 + font.width(name) + PADDING + font.width(tags);
        float scale = card.lookAtScale;
        int scaledWidth = Math.round(width * scale);
        int scaledHeight = Math.round(HEIGHT * scale);

        int x = switch (card.lookAtAnchor) {
            case BELOW_CROSSHAIR, TOP -> (graphics.guiWidth() - scaledWidth) / 2;
            case BOTTOM_LEFT -> EDGE;
            case BOTTOM_RIGHT -> graphics.guiWidth() - scaledWidth - EDGE;
        };
        int y = switch (card.lookAtAnchor) {
            case BELOW_CROSSHAIR -> graphics.guiHeight() / 2 + BELOW_CROSSHAIR;
            case TOP -> EDGE;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> graphics.guiHeight() - scaledHeight - EDGE;
        };

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.fill(0, 0, width, HEIGHT, Theme.fade(Theme.withAlpha(0x191919, card.lookAtOpacity), alpha));
        graphics.outline(0, 0, width, HEIGHT, Theme.fade(Theme.BORDER, alpha));
        graphics.text(font, name, PADDING, 4, Theme.fade(Theme.TEXT, alpha));
        graphics.text(font, tags, PADDING * 2 + font.width(name), 4, Theme.fade(0xFFFFFFFF, alpha));
        graphics.pose().popMatrix();
    }
}
