package dev.tierprofiles.hud;

import com.mojang.authlib.GameProfile;
import dev.tierprofiles.core.settings.Cosmetics;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
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

        @Override
        public boolean isInvisible() {
            return false;
        }
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
