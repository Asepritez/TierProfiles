package dev.tierprofiles.hud;

import com.mojang.authlib.GameProfile;
import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.net.ProfileService;
import dev.tierprofiles.core.settings.Cosmetics;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The player model on a card. It's a separate pretend player that is never added to
 * the world: the server doesn't know it exists, and it's nothing to do with the real
 * player's entity. Whatever the card shows, such as card armor, stays on this copy.
 */
public final class Portrait {
    private static final float FOOT_OFFSET = 0.0625f;

    private static Model current;
    /** Skins of players not in your tab list, fetched once each. An empty entry means it's on its way. */
    private static final Map<UUID, Optional<Supplier<PlayerSkin>>> SKINS = new ConcurrentHashMap<>();
    private static final int MAX_SKINS = 64;
    private static final Executor SKIN_LOOKUPS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TierProfiles card skins");
        thread.setDaemon(true);
        return thread;
    });

    private Portrait() {
    }

    private static final class Model extends RemotePlayer {
        Model(ClientLevel level, UUID uuid, String name) {
            super(level, new GameProfile(uuid, name));
            setId(-1);
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        /**
         * Someone in your tab list wears the skin the game already has for them. Anyone
         * else, such as a player found in search, gets theirs looked up by UUID.
         */
        @Override
        public PlayerSkin getSkin() {
            if (getPlayerInfo() != null) {
                return super.getSkin();
            }
            Supplier<PlayerSkin> found = skinOf(getUUID(), getGameProfile().name());
            return found != null ? found.get() : super.getSkin();
        }

        @Override
        public boolean isInvisible() {
            return false;
        }

        /**
         * The skin's outer layer (hat, jacket, sleeves, trousers). A pretend player starts
         * with all of it off, which leaves the skin flat, so copy what the real player
         * chose: your own skin options for you, or their entity if they're nearby.
         */
        @Override
        public boolean isModelPartShown(PlayerModelPart part) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.getUUID().equals(getUUID())) {
                return minecraft.options.isModelPartEnabled(part);
            }
            Player real = level().getPlayerByUUID(getUUID());
            return real == null || real == this || real.isModelPartShown(part);
        }
    }

    /**
     * Looks the player's profile up on Mojang's session server, which the game also does for
     * everyone in your tab list, then lets the game's own skin manager load the texture.
     * Only while lookups are allowed. Returns null until it's ready.
     */
    private static Supplier<PlayerSkin> skinOf(UUID uuid, String name) {
        Optional<Supplier<PlayerSkin>> known = SKINS.get(uuid);
        if (known != null) {
            return known.orElse(null);
        }
        if (!ProfileService.isAllowed(TierProfiles.config().global) || !ProfileService.isLookupEligible(uuid)) {
            return null;
        }
        if (SKINS.size() >= MAX_SKINS) {
            SKINS.clear();
        }
        SKINS.put(uuid, Optional.empty());
        Minecraft minecraft = Minecraft.getInstance();
        SKIN_LOOKUPS.execute(() -> {
            try {
                var result = minecraft.services().sessionService().fetchProfile(uuid, false);
                GameProfile profile = result != null ? result.profile() : new GameProfile(uuid, name);
                Supplier<PlayerSkin> lookup = minecraft.getSkinManager().createLookup(profile, false);
                SKINS.put(uuid, Optional.of(lookup));
            } catch (RuntimeException e) {
                // Mojang didn't answer. The card keeps the default skin, and tries again later.
                SKINS.remove(uuid);
            }
        });
        return null;
    }

    static RemotePlayer model(UUID uuid, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            current = null;
            return null;
        }
        if (current == null || current.level() != minecraft.level || !current.getUUID().equals(uuid)) {
            current = new Model(minecraft.level, uuid, name);
        }
        return current;
    }

    /**
     * Draws the model facing the viewer, turned by `yaw` degrees.
     *
     * @param look card armor to wear, or null for none
     */
    public static void draw(GuiGraphicsExtractor graphics, UUID uuid, String name, int x0, int y0, int x1, int y1, int size, float yaw, Cosmetics look) {
        RemotePlayer model = model(uuid, name);
        if (model == null) {
            return;
        }
        Wardrobe.dress(model, look);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super RemotePlayer, ?> renderer = dispatcher.getRenderer(model);
        EntityRenderState state = renderer.createRenderState(model, 1.0f);
        state.shadowPieces.clear();
        state.outlineColor = 0;

        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0f + yaw;
            living.yRot = 0;
            living.xRot = 0;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1.0f;
        }

        Vector3f translation = new Vector3f(0, state.boundingBoxHeight / 2.0f + FOOT_OFFSET, 0);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        //? if >=26.1 {
        graphics.entity(state, size, translation, rotation, null, x0, y0, x1, y1);
        //?} else {
        /*graphics.submitEntityRenderState(state, size, translation, rotation, null, x0, y0, x1, y1);
        *///?}
    }
}
