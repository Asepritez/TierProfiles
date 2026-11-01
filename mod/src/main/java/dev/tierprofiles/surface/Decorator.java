package dev.tierprofiles.surface;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.logic.HighlightEngine;
import dev.tierprofiles.core.logic.NameStyle;
import dev.tierprofiles.core.logic.Tag;
import dev.tierprofiles.core.logic.TagFormatter;
import dev.tierprofiles.core.logic.TagSelector;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Style;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.core.settings.SurfaceSettings;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Where tags meet Minecraft text. Every mixin goes through here, and it runs while
 * the game draws, so it only reads memory. A mistake here must never crash anyone's
 * game mid-fight, so failures fall back to the untouched name.
 */
public final class Decorator {
    private static boolean loggedFailure;

    private Decorator() {
    }

    public static TagSelector.Context context(UUID uuid) {
        return new TagSelector.Context(Minecraft.getInstance().isLocalPlayer(uuid), TierProfiles.detectedKit());
    }

    /** The name with its tag and highlight, for nametags and the tab list. */
    public static Component name(UUID uuid, Component name, Surface surface) {
        try {
            return decorate(uuid, name, surface);
        } catch (RuntimeException e) {
            logOnce(e);
            return name;
        }
    }

    private static Component decorate(UUID uuid, Component name, Surface surface) {
        PlayerProfile profile = TierProfiles.profiles().profile(uuid);
        Settings settings = TierProfiles.settings();
        SurfaceSettings surfaceSettings = settings.surface(surface);
        if (profile == null || !surfaceSettings.enabled) {
            return name;
        }

        NameStyle nameStyle = surfaceSettings.highlightName
                ? HighlightEngine.evaluate(profile, settings.rules, settings.filters)
                : NameStyle.NONE;
        Component styledName = TagText.styleName(name, nameStyle);

        List<Tag> tags = TagSelector.select(profile, surface, settings, context(uuid));
        if (tags.isEmpty()) {
            return styledName;
        }

        MutableComponent tag = TagText.build(TagFormatter.format(tags, surfaceSettings, settings.style), settings.style);
        if (nameStyle.glow()) {
            TagText.applyGlow(tag, nameStyle);
        }

        return settings.style.position == Style.Position.BEFORE
                ? Component.empty().append(tag).append(" ").append(styledName)
                : Component.empty().append(styledName).append(" ").append(tag);
    }

    /**
     * Chat tags go in front of the whole message. A server's chat format can be
     * anything, so we can't reliably find where the name sits inside it.
     */
    public static Component chat(UUID uuid, Component message) {
        try {
            Settings settings = TierProfiles.settings();
            SurfaceSettings surface = settings.surface(Surface.CHAT);
            if (!surface.enabled) {
                return message;
            }
            PlayerProfile profile = TierProfiles.profiles().profile(uuid);
            List<Tag> tags = TagSelector.select(profile, Surface.CHAT, settings, context(uuid));
            if (tags.isEmpty()) {
                return message;
            }
            return Component.empty()
                    .append(TagText.build(TagFormatter.format(tags, surface, settings.style), settings.style))
                    .append(" ")
                    .append(message);
        } catch (RuntimeException e) {
            logOnce(e);
            return message;
        }
    }

    private static void logOnce(RuntimeException e) {
        if (!loggedFailure) {
            loggedFailure = true;
            TierProfiles.LOGGER.error("Could not build a tag", e);
        }
    }
}
