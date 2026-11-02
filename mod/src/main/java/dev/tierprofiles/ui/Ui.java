package dev.tierprofiles.ui;

import dev.tierprofiles.core.model.Tier;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A small immediate-mode UI kit. Instead of keeping a tree of widget objects, a screen
 * redraws everything each frame and asks questions as it goes: "draw a button here,
 * and was it clicked?" Values live in the settings themselves, so nothing can get out
 * of step with what's on screen.
 *
 * The look follows the spec's flat style, with ideas from Tallium (see
 * THIRD_PARTY_NOTICES.txt).
 */
public final class Ui {
    public static final int CONTROL_HEIGHT = 18;
    public static final int ROW_HEIGHT = 19;

    private static final int SWITCH_WIDTH = 22;
    private static final int SWITCH_HEIGHT = 10;
    private static final int TIER_CHIP_WIDTH = 15;
    private static final int TIER_CHIP_HEIGHT = 12;

    public GuiGraphicsExtractor graphics;
    public final Font font = Minecraft.getInstance().font;
    public int mouseX;
    public int mouseY;

    private boolean clicked;
    private boolean rightClicked;
    private boolean mouseDown;
    private Object dragging;

    private boolean clipping;
    private int clipTop;
    private int clipBottom;
    /** True while a popup is open, so controls beneath it ignore the mouse. */
    private boolean blocked;
    private String hoverHint;

    public void begin(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.graphics = graphics;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hoverHint = null;
    }

    /** Clicks only count for one frame. */
    public void end() {
        clicked = false;
        rightClicked = false;
    }

    public void onMousePressed(boolean rightButton) {
        if (rightButton) {
            rightClicked = true;
        } else {
            clicked = true;
            mouseDown = true;
        }
    }

    public void onMouseReleased() {
        mouseDown = false;
        dragging = null;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void clip(int x, int top, int right, int bottom) {
        graphics.enableScissor(x, top, right, bottom);
        clipping = true;
        clipTop = top;
        clipBottom = bottom;
    }

    public void unclip() {
        graphics.disableScissor();
        clipping = false;
    }

    public boolean hovered(int x, int y, int width, int height) {
        if (blocked || (clipping && (mouseY < clipTop || mouseY >= clipBottom))) {
            return false;
        }
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean clicked(int x, int y, int width, int height) {
        return clicked && hovered(x, y, width, height);
    }

    public boolean rightClicked(int x, int y, int width, int height) {
        return rightClicked && hovered(x, y, width, height);
    }

    /** Eats the click, so whatever is beneath this panel doesn't also get it. */
    public void consumeClick() {
        clicked = false;
        rightClicked = false;
    }

    public void hint(String text) {
        hoverHint = text;
    }

    public String hoverHint() {
        return hoverHint;
    }

    public void text(String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
    }

    public void text(Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
    }

    public void panel(int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, Theme.PANEL);
        graphics.outline(x, y, width, height, Theme.BORDER);
    }

    public boolean button(int x, int y, int width, int height, String label, boolean primary) {
        boolean hover = hovered(x, y, width, height);
        graphics.fill(x, y, x + width, y + height, hover ? Theme.SELECTED : Theme.ROW);
        graphics.outline(x, y, width, height, primary || hover ? Theme.ACCENT : Theme.BORDER);
        graphics.centeredText(font, label, x + width / 2, y + (height - 8) / 2, primary ? Theme.ACCENT : Theme.TEXT);
        return clicked(x, y, width, height);
    }

    /** An on/off switch. Returns the new value. */
    public boolean toggle(int rightEdge, int centerY, boolean value) {
        int x = rightEdge - SWITCH_WIDTH;
        int y = centerY - SWITCH_HEIGHT / 2;
        graphics.fill(x, y, x + SWITCH_WIDTH, y + SWITCH_HEIGHT, value ? 0xFF2C5A35 : Theme.ROW);
        graphics.outline(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, value ? Theme.GOOD : Theme.BORDER);
        int knobX = value ? x + SWITCH_WIDTH - 8 : x + 2;
        graphics.fill(knobX, y + 2, knobX + 6, y + SWITCH_HEIGHT - 2, value ? Theme.GOOD : Theme.MUTED);
        return clicked(x - 4, centerY - 8, SWITCH_WIDTH + 8, 16) != value;
    }

    /** A box that steps through choices. Left click goes forward and right click goes back. */
    public <T> T cycle(int rightEdge, int centerY, int width, List<T> values, T value, java.util.function.Function<T, String> name) {
        int x = rightEdge - width;
        int y = centerY - 7;
        boolean hover = hovered(x, y, width, 14);
        graphics.fill(x, y, x + width, y + 14, hover ? Theme.SELECTED : Theme.ROW);
        graphics.outline(x, y, width, 14, hover ? Theme.ACCENT : Theme.BORDER);
        graphics.centeredText(font, name.apply(value), x + width / 2, y + 3, Theme.TEXT);
        text("‹", x + 3, y + 3, Theme.MUTED);
        text("›", x + width - 6, y + 3, Theme.MUTED);

        int index = Math.max(0, values.indexOf(value));
        if (clicked(x, y, width, 14)) {
            return values.get((index + 1) % values.size());
        }
        if (rightClicked(x, y, width, 14)) {
            return values.get((index + values.size() - 1) % values.size());
        }
        return value;
    }

    public float slider(Object id, int rightEdge, int centerY, int width, float value, float min, float max, String label) {
        int labelWidth = 34;
        int x = rightEdge - width;
        int trackWidth = width - labelWidth;
        int y = centerY - 1;

        if (clicked(x, centerY - 6, trackWidth, 12)) {
            dragging = id;
        }
        float result = value;
        if (dragging == id && mouseDown) {
            float t = Math.clamp((float) (mouseX - x) / trackWidth, 0, 1);
            result = min + (max - min) * t;
        }

        float shown = (result - min) / (max - min);
        int knob = x + Math.round(shown * (trackWidth - 4));
        graphics.fill(x, y, x + trackWidth, y + 2, Theme.BORDER);
        graphics.fill(x, y, knob, y + 2, Theme.ACCENT);
        graphics.fill(knob, centerY - 5, knob + 4, centerY + 5, hovered(x, centerY - 6, trackWidth, 12) ? 0xFFFFFFFF : Theme.TEXT);
        text(label, rightEdge - labelWidth + 4, centerY - 4, Theme.MUTED);
        return result;
    }

    /** A row of tiers to choose from, or `None`. Tiers below the choice are dimmed. */
    public Tier tierPicker(int rightEdge, int centerY, Tier value, java.util.function.ToIntFunction<Tier> color) {
        List<Tier> tiers = Tier.STANDARD;
        int noneWidth = 26;
        int x = rightEdge - tiers.size() * TIER_CHIP_WIDTH - noneWidth - 2;
        int y = centerY - TIER_CHIP_HEIGHT / 2;

        boolean none = value == null;
        graphics.fill(x, y, x + noneWidth, y + TIER_CHIP_HEIGHT, none ? Theme.SELECTED : Theme.ROW);
        graphics.outline(x, y, noneWidth, TIER_CHIP_HEIGHT, none ? Theme.ACCENT : Theme.BORDER);
        graphics.centeredText(font, "All", x + noneWidth / 2, y + 2, Theme.TEXT);
        Tier result = value;
        if (clicked(x, y, noneWidth, TIER_CHIP_HEIGHT)) {
            result = null;
        }

        x += noneWidth + 2;
        for (Tier tier : tiers) {
            boolean included = value == null || tier.isAtLeast(value);
            int rgb = color.applyAsInt(tier);
            graphics.fill(x, y, x + TIER_CHIP_WIDTH - 1, y + TIER_CHIP_HEIGHT, included ? Theme.opaque(rgb) : Theme.withAlpha(rgb, 0.18f));
            if (tier.equals(value)) {
                graphics.outline(x, y, TIER_CHIP_WIDTH - 1, TIER_CHIP_HEIGHT, 0xFFFFFFFF);
            }
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 1, y + 3);
            graphics.pose().scale(0.75f, 0.75f);
            graphics.text(font, tier.label(), 0, 0, included ? 0xFF101010 : Theme.MUTED, false);
            graphics.pose().popMatrix();
            if (hovered(x, y, TIER_CHIP_WIDTH - 1, TIER_CHIP_HEIGHT)) {
                hint("Show " + tier.label() + " and above");
                if (clicked(x, y, TIER_CHIP_WIDTH - 1, TIER_CHIP_HEIGHT)) {
                    result = tier;
                }
            }
            x += TIER_CHIP_WIDTH;
        }
        return result;
    }

    public boolean swatch(int x, int y, int size, int rgb) {
        graphics.fill(x, y, x + size, y + size, Theme.opaque(rgb));
        graphics.outline(x, y, size, size, hovered(x, y, size, size) ? 0xFFFFFFFF : Theme.BORDER);
        return clicked(x, y, size, size);
    }

    public int width(String text) {
        return font.width(text);
    }
}
