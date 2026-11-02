package dev.tierprofiles.hud;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.settings.Cosmetics;
import dev.tierprofiles.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Card backgrounds, borders and effects. Everything is drawn inside the card, from the
 * clock and a fixed pattern of pseudo-random numbers, so it never touches the world and
 * looks the same at any frame rate. With reduce motion on, it all holds still.
 *
 * The moon is the game's own texture, loaded at runtime and never bundled.
 */
public final class CardStyle {
    private static final Identifier MOON = Identifier.withDefaultNamespace("textures/environment/celestial/moon/full_moon.png");
    private static final int MOON_TEXTURE = 32;
    private static final int MOON_BODY = 12;
    private static final int MOON_PIXEL = 3;
    private static final int MOON_SIZE = 8 * MOON_PIXEL;
    private static final int MOON_GLOW = 0x96B2FF;
    private static final int GLOW_REACH = 30;
    private static final int GLOW_BLOCK = 3;
    private static final int GLOW_STRENGTH = 0xB0;
    /** How far the moon and its glow reach from the centre, which the name must stay clear of. */
    public static final int MOON_REACH = MOON_SIZE / 2 + 6;
    private static final int STARS = 70;
    private static final int MOTES = 26;
    private static final int SKY_OPACITY = 0xD0;

    private CardStyle() {
    }

    private static long now() {
        return TierProfiles.config().global.reduceMotion ? 0 : CardState.now();
    }

    /** A repeatable random number from 0 to 1, so patterns stay put between frames. */
    private static float noise(int seed) {
        int value = seed * 1664525 + 1013904223;
        value ^= value >>> 13;
        value *= 1274126177;
        value ^= value >>> 16;
        return (value & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static int shade(int rgb, float alpha) {
        return Theme.fade(0xFF000000 | rgb, alpha);
    }

    public static void drawBackground(GuiGraphicsExtractor graphics, Cosmetics look, int width, int height, float alpha) {
        drawScene(graphics, look.background, width, height, alpha, now());
    }

    private static void drawScene(GuiGraphicsExtractor graphics, String scene, int width, int height, float alpha, long time) {
        switch (scene) {
            case "moon" -> {
                sky(graphics, 0x0A1030, 0x1C2A5E, width, height, alpha);
                stars(graphics, width, height, alpha, time, 1);
                hills(graphics, width, height, alpha);
            }
            case "starfield" -> {
                sky(graphics, 0x05070F, 0x0B1124, width, height, alpha);
                stars(graphics, width, height, alpha, time, 2);
            }
            case "nebula" -> {
                sky(graphics, 0x0A0520, 0x1A0A3A, width, height, alpha);
                nebula(graphics, width, height, alpha, time);
                stars(graphics, width, height, alpha, time, 1);
            }
            case "embers" -> {
                sky(graphics, 0x140504, 0x4A1408, width, height, alpha);
                rising(graphics, width, height, alpha, time, 0xFF8A1F, -1, 0.012f);
            }
            case "snow" -> {
                sky(graphics, 0x0E1C34, 0x2A4468, width, height, alpha);
                rising(graphics, width, height, alpha, time, 0xFFFFFF, 1, 0.009f);
                graphics.fill(0, height - 5, width, height, shade(0xDDE8F4, alpha * 0.7f));
            }
            default -> {
            }
        }
    }

    private static void sky(GuiGraphicsExtractor graphics, int top, int bottom, int width, int height, float alpha) {
        int fade = Math.round(SKY_OPACITY * alpha) << 24;
        graphics.fillGradient(0, 0, width, height, fade | top, fade | bottom);
    }

    private static void stars(GuiGraphicsExtractor graphics, int width, int height, float alpha, long time, int size) {
        for (int i = 0; i < STARS; i++) {
            int x = Math.round(noise(i) * (width - 2));
            int y = Math.round(noise(i + 1000) * (height - 2));
            float twinkle = 0.45f + 0.55f * Mth.sin(time / 500f + noise(i + 2000) * 6.28f) * Mth.sin(time / 500f + noise(i + 2000) * 6.28f);
            int dot = i % 7 == 0 ? size + 1 : size;
            graphics.fill(x, y, x + dot, y + dot, shade(0xFFFFFF, alpha * twinkle));
        }
    }

    private static void hills(GuiGraphicsExtractor graphics, int width, int height, float alpha) {
        int step = 6;
        for (int x = 0; x < width; x += step) {
            int rise = 3 + Math.round(noise(x / step + 3000) * 7);
            graphics.fill(x, height - rise, x + step, height, shade(0x0A0F24, alpha));
        }
    }

    private static void nebula(GuiGraphicsExtractor graphics, int width, int height, float alpha, long time) {
        int blocks = 40;
        for (int i = 0; i < blocks; i++) {
            float drift = Mth.sin(time / 3000f + i) * 6;
            int x = Math.round(noise(i + 5000) * width + drift);
            int y = Math.round(noise(i + 6000) * height);
            int size = 14 + Math.round(noise(i + 7000) * 26);
            int color = i % 2 == 0 ? 0xC03AA0 : 0x5A3AE0;
            graphics.fill(x - size, y - size / 2, x + size, y + size / 2, Theme.withAlpha(color, 0.07f * alpha));
        }
    }

    /** Particles that wrap around: embers float up, and snow drifts down. */
    private static void rising(GuiGraphicsExtractor graphics, int width, int height, float alpha, long time, int color, int direction, float speed) {
        for (int i = 0; i < MOTES * 2; i++) {
            float travel = (noise(i + 8000) * height + time * speed * (0.6f + noise(i + 8500))) % height;
            int y = Math.round(direction > 0 ? travel : height - travel);
            int x = Math.round(noise(i + 9000) * width + Mth.sin(time / 900f + i) * 4);
            int size = i % 4 == 0 ? 2 : 1;
            float life = direction > 0 ? 1 : (float) y / height;
            graphics.fill(x, y, x + size, y + size, shade(color, alpha * (0.3f + 0.7f * life)));
        }
    }

    /**
     * The game's own moon. Its texture is 32 by 32 with an 8 by 8 body in the middle, and
     * a plain square at this size reads as a broken block, so the four corner pixels are
     * dropped to make a disc. The glow is drawn as soft light behind it, clipped to the
     * strip, and it pulses gently.
     *
     * @param centerX where the moon is centred across the card
     * @param centerY where it's centred down the strip
     */
    public static void drawMoon(GuiGraphicsExtractor graphics, int centerX, int centerY, int stripHeight, int cardWidth, float alpha) {
        float pulse = 0.85f + 0.15f * Mth.sin(now() / 1400f);
        graphics.enableScissor(0, 0, cardWidth, stripHeight);
        // Soft light that falls off with distance, in blocks so it stays pixel art.
        for (int dy = -GLOW_REACH; dy < GLOW_REACH; dy += GLOW_BLOCK) {
            for (int dx = -GLOW_REACH; dx < GLOW_REACH; dx += GLOW_BLOCK) {
                float distance = Mth.sqrt(dx * dx + dy * dy) / GLOW_REACH;
                if (distance < 1) {
                    int light = Math.round((1 - distance) * (1 - distance) * GLOW_STRENGTH * pulse * alpha);
                    graphics.fill(centerX + dx, centerY + dy, centerX + dx + GLOW_BLOCK, centerY + dy + GLOW_BLOCK, light << 24 | MOON_GLOW);
                }
            }
        }
        graphics.disableScissor();

        int left = centerX - MOON_SIZE / 2;
        int top = centerY - MOON_SIZE / 2;
        int tint = Theme.fade(0xFFFFFFFF, alpha);
        // Drawn as three bands, which leaves out the four corner pixels.
        moonPart(graphics, left + MOON_PIXEL, top, 1, 0, 6, 1, tint);
        moonPart(graphics, left, top + MOON_PIXEL, 0, 1, 8, 6, tint);
        moonPart(graphics, left + MOON_PIXEL, top + 7 * MOON_PIXEL, 1, 7, 6, 1, tint);
    }

    private static void moonPart(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int width, int height, int tint) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, MOON, x, y, MOON_BODY + u, MOON_BODY + v, width * MOON_PIXEL, height * MOON_PIXEL,
                width, height, MOON_TEXTURE, MOON_TEXTURE, tint);
    }

    public static void drawEffects(GuiGraphicsExtractor graphics, Cosmetics look, int width, int height, float alpha) {
        if (look.effect.equals("off") || look.intensity == 0) {
            return;
        }
        long time = now();
        float strength = look.intensity / 100f;
        if (look.effect.equals("sparkles") || look.effect.equals("both")) {
            sparkles(graphics, width, height, alpha, time, strength, look.accent);
        }
        if (look.effect.equals("motes") || look.effect.equals("both")) {
            motes(graphics, width, height, alpha, time, strength, look.accent);
        }
    }

    private static void sparkles(GuiGraphicsExtractor graphics, int width, int height, float alpha, long time, float strength, int accent) {
        int count = Math.round(14 * strength);
        for (int i = 0; i < count; i++) {
            float pulse = (time / 1600f + noise(i + 100)) % 1;
            float brightness = Mth.sin(pulse * Mth.PI);
            int x = Math.round(noise(i + 200 + (int) (time / 1600)) * (width - 6)) + 3;
            int y = Math.round(noise(i + 300 + (int) (time / 1600)) * (height - 6)) + 3;
            int arm = brightness > 0.7f ? 2 : 1;
            int color = shade(accent, alpha * brightness);
            graphics.fill(x - arm, y, x + arm + 1, y + 1, color);
            graphics.fill(x, y - arm, x + 1, y + arm + 1, color);
        }
    }

    private static void motes(GuiGraphicsExtractor graphics, int width, int height, float alpha, long time, float strength, int accent) {
        int count = Math.round(MOTES * strength);
        for (int i = 0; i < count; i++) {
            float x = (noise(i + 400) * width + time * 0.006f * (0.5f + noise(i + 450))) % width;
            float y = noise(i + 500) * height + Mth.sin(time / 1200f + i) * 5;
            graphics.fill(Math.round(x), Math.round(y), Math.round(x) + 1, Math.round(y) + 1, shade(accent, alpha * 0.6f));
        }
    }

    public static void drawBorder(GuiGraphicsExtractor graphics, Cosmetics look, int width, int height, float alpha) {
        int accent = shade(look.accent, alpha);
        switch (look.border) {
            case "double" -> {
                graphics.outline(0, 0, width, height, accent);
                graphics.outline(2, 2, width - 4, height - 4, shade(look.accent, alpha * 0.5f));
            }
            case "glow" -> {
                for (int ring = 0; ring < 4; ring++) {
                    graphics.outline(ring, ring, width - 2 * ring, height - 2 * ring, shade(look.accent, alpha * (0.8f - ring * 0.22f)));
                }
            }
            case "shimmer" -> {
                graphics.outline(0, 0, width, height, shade(look.accent, alpha * 0.5f));
                shimmer(graphics, width, height, alpha, look.accent);
            }
            case "corners" -> {
                graphics.outline(0, 0, width, height, shade(look.accent, alpha * 0.3f));
                corners(graphics, width, height, accent);
            }
            default -> graphics.outline(0, 0, width, height, shade(0x2E3338, alpha));
        }
    }

    /** Two bright dashes chasing each other around the edge. */
    private static void shimmer(GuiGraphicsExtractor graphics, int width, int height, float alpha, int accent) {
        int perimeter = 2 * (width + height);
        int length = 30;
        long time = now();
        for (int chaser = 0; chaser < 2; chaser++) {
            int head = (int) ((time / 8 + chaser * perimeter / 2) % perimeter);
            for (int i = 0; i < length; i++) {
                int step = Math.floorMod(head - i, perimeter);
                float fade = 1 - (float) i / length;
                int color = shade(accent == 0 ? 0xFFFFFF : brighten(accent), alpha * fade);
                point(graphics, step, width, height, color);
            }
        }
    }

    private static int brighten(int rgb) {
        int red = Math.min(255, (rgb >> 16 & 0xFF) + 80);
        int green = Math.min(255, (rgb >> 8 & 0xFF) + 80);
        int blue = Math.min(255, (rgb & 0xFF) + 80);
        return red << 16 | green << 8 | blue;
    }

    private static void point(GuiGraphicsExtractor graphics, int step, int width, int height, int color) {
        int x;
        int y;
        if (step < width) {
            x = step;
            y = 0;
        } else if (step < width + height) {
            x = width - 1;
            y = step - width;
        } else if (step < 2 * width + height) {
            x = 2 * width + height - step - 1;
            y = height - 1;
        } else {
            x = 0;
            y = 2 * (width + height) - step - 1;
        }
        graphics.fill(x, y, x + 1, y + 1, color);
    }

    private static void corners(GuiGraphicsExtractor graphics, int width, int height, int color) {
        int length = 12;
        int thick = 2;
        graphics.fill(0, 0, length, thick, color);
        graphics.fill(0, 0, thick, length, color);
        graphics.fill(width - length, 0, width, thick, color);
        graphics.fill(width - thick, 0, width, length, color);
        graphics.fill(0, height - thick, length, height, color);
        graphics.fill(0, height - length, thick, height, color);
        graphics.fill(width - length, height - thick, width, height, color);
        graphics.fill(width - thick, height - length, width, height, color);
    }

    /** A small picture of a background for the settings. */
    public static void drawThumbnail(GuiGraphicsExtractor graphics, String background, int x, int y, int width, int height) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.fill(0, 0, width, height, 0xFF10141A);
        graphics.outline(0, 0, width, height, Theme.SLOT_EDGE);
        graphics.enableScissor(0, 0, width, height);
        graphics.pose().pushMatrix();
        graphics.pose().scale(0.5f, 0.5f);
        drawScene(graphics, background, width * 2, height * 2, 1, now());
        if (background.equals("moon")) {
            drawMoon(graphics, width * 2 - 16, height, height * 2, width * 2, 1);
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();
        graphics.pose().popMatrix();
    }
}
