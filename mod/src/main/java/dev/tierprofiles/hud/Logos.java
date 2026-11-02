package dev.tierprofiles.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.ui.Theme;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws list logos. A downloaded logo becomes a live texture straight away. Until then,
 * or if the download failed, the logo glyph shows our neutral letter mark.
 */
public final class Logos {
    private static final int TEXTURE_SIZE = LogoStore.SIZE;
    private static final int MARK_SIZE = 16;
    private static final Map<TierList, Identifier> loaded = new EnumMap<>(TierList.class);

    private Logos() {
    }

    public static void draw(GuiGraphicsExtractor graphics, TierList list, int x, int y, int size, float alpha) {
        int tint = Theme.fade(0xFFFFFFFF, alpha);
        Identifier live = loaded.get(list);
        if (live == null) {
            live = tryLoad(list);
        }
        if (live != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, live, x, y, 0, 0, size, size, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, tint);
            return;
        }
        Identifier mark = Identifier.fromNamespaceAndPath(TierProfiles.MOD_ID, "textures/list/" + list.id() + ".png");
        graphics.blit(RenderPipelines.GUI_TEXTURED, mark, x, y, 0, 0, size, size, MARK_SIZE, MARK_SIZE, MARK_SIZE, MARK_SIZE, tint);
    }

    /** Turns a finished download into a texture. Only ever runs on the render thread. */
    private static Identifier tryLoad(TierList list) {
        if (!LogoStore.isReady(list)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(LogoStore.file(list.id()))) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath(TierProfiles.MOD_ID, "logo_live/" + list.id());
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
            loaded.put(list, id);
            return id;
        } catch (IOException | RuntimeException e) {
            LogoStore.markBroken(list);
            return null;
        }
    }
}
