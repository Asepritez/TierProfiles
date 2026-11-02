package dev.tierprofiles.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.ui.Theme;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws list logos and title badges. A downloaded image becomes a live texture straight
 * away. Until then, or if the download failed, logos show our neutral letter mark.
 */
public final class Logos {
    private static final int TEXTURE_SIZE = LogoStore.SIZE;
    private static final int MARK_SIZE = 16;
    private static final Map<String, Identifier> loaded = new HashMap<>();

    private Logos() {
    }

    public static void draw(GuiGraphicsExtractor graphics, TierList list, int x, int y, int size, float alpha) {
        int tint = Theme.fade(0xFFFFFFFF, alpha);
        if (drawDownloaded(graphics, list.id(), x, y, size, tint)) {
            return;
        }
        Identifier mark = Identifier.fromNamespaceAndPath(TierProfiles.MOD_ID, "textures/list/" + list.id() + ".png");
        graphics.blit(RenderPipelines.GUI_TEXTURED, mark, x, y, 0, 0, size, size, MARK_SIZE, MARK_SIZE, MARK_SIZE, MARK_SIZE, tint);
    }

    /** Returns false when there's no downloaded image yet. */
    static boolean drawDownloaded(GuiGraphicsExtractor graphics, String id, int x, int y, int size, int tint) {
        Identifier live = loaded.get(id);
        if (live == null) {
            live = tryLoad(id);
        }
        if (live == null) {
            return false;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, live, x, y, 0, 0, size, size, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, tint);
        return true;
    }

    /** Turns a finished download into a texture. Only ever runs on the render thread. */
    private static Identifier tryLoad(String id) {
        if (!LogoStore.isReady(id)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(LogoStore.file(id))) {
            NativeImage image = NativeImage.read(in);
            Identifier texture = Identifier.fromNamespaceAndPath(TierProfiles.MOD_ID, "logo_live/" + id);
            Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(texture::toString, image));
            loaded.put(id, texture);
            return texture;
        } catch (IOException | RuntimeException e) {
            LogoStore.markBroken(id);
            return null;
        }
    }
}
