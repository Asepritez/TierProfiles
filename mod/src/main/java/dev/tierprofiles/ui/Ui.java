package dev.tierprofiles.ui;

import dev.tierprofiles.core.model.Tier;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A small immediate-mode UI kit. Instead of keeping a tree of widget objects, a screen
 * redraws everything each frame and asks questions as it goes: "draw a button here, and
 * was it clicked?" Values live in the settings themselves, so nothing can get out of
 * step with what's on screen.
 *
 * Choices are drawn like the vanilla hotbar, with a bright frame around the selected
 * slot. The flat style takes ideas from Tallium (see THIRD_PARTY_NOTICES.txt).
 */
public final class Ui {
    public static final int SLOT = 20;
    private static final int FRAME = 24;
    private static final int SWITCH_WIDTH = 26;
    private static final int SWITCH_HEIGHT = 12;
    private static final int SEGMENT_PADDING = 7;
    private static final int TIER_CELL = 28;

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
    private boolean blocked;
    private String hoverHint;
    private String hoverTitle;

    public void begin(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.graphics = graphics;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hoverHint = null;
        hoverTitle = null;
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

    public boolean mouseIsDown() {
        return mouseDown;
    }

    /** True for a click that nothing has used yet. */
    public boolean anyClick() {
        return clicked;
    }

    /** True while a popup is open, so controls beneath it ignore the mouse. */
    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
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

    public void startDrag(Object id) {
        dragging = id;
    }

    public boolean isDragging(Object id) {
        return dragging == id && mouseDown;
    }

    public void hint(String title, String text) {
        hoverTitle = title;
        hoverHint = text;
    }

    public String hoverHint() {
        return hoverHint;
    }

    public String hoverTitle() {
        return hoverTitle;
    }

    public void text(String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
    }

    public void text(Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
    }

    public int width(String text) {
        return font.width(text);
    }

    public void bigText(String text, int x, int y, float scale, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color);
        graphics.pose().popMatrix();
    }

    /**
     * True once the game has finished loading item data. A screen can open while the
     * loading overlay is still up, as the first-run notice does on a fresh install, and
     * making an ItemStack before this point crashes the game.
     */
    public static boolean itemsReady() {
        //? if >=26.1 {
        return Items.STONE.builtInRegistryHolder().areComponentsBound();
        //?} else {
        /*// Before 26.1, item data is ready as soon as the game has started.
        return true;
        *///?}
    }

    /** Draws an item, or nothing at all until items are ready. */
    public void item(Item item, int x, int y) {
        if (itemsReady()) {
            graphics.item(new ItemStack(item), x, y);
        }
    }

    public void panel(int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, Theme.INK);
        graphics.outline(x, y, width, height, Theme.LINE);
    }

    public boolean button(int x, int y, int width, int height, String label, boolean primary) {
        boolean hover = hovered(x, y, width, height);
        graphics.fill(x, y, x + width, y + height, hover ? Theme.SELECTED : Theme.SLOT);
        graphics.outline(x, y, width, height, primary || hover ? Theme.ACCENT : Theme.BORDER);
        graphics.centeredText(font, label, x + width / 2, y + (height - 8) / 2, primary ? 0xFFFFFFFF : Theme.TEXT);
        return clicked(x, y, width, height);
    }

    /** The same bright frame the vanilla hotbar puts around your selected slot. */
    public void frame(int x, int y, int width, int height) {
        graphics.outline(x - 2, y - 2, width + 4, height + 4, 0xFFFFFFFF);
        graphics.outline(x - 1, y - 1, width + 2, height + 2, 0xFFFFFFFF);
    }

    /** One empty inventory slot. */
    public boolean slot(int x, int centerY, boolean selected) {
        int y = centerY - SLOT / 2;
        boolean hover = hovered(x, y, SLOT, SLOT);
        graphics.fill(x, y, x + SLOT, y + SLOT, hover ? Theme.SELECTED : Theme.SLOT);
        graphics.outline(x, y, SLOT, SLOT, Theme.SLOT_EDGE);
        if (selected) {
            frame(x, y, SLOT, SLOT);
        }
        return clicked(x, y, SLOT, SLOT);
    }

    /** An on or off switch, with its state written beside it. Returns the new value. */
    public boolean toggle(int x, int centerY, boolean value) {
        int y = centerY - SWITCH_HEIGHT / 2;
        graphics.fill(x, y, x + SWITCH_WIDTH, y + SWITCH_HEIGHT, Theme.ROW);
        graphics.outline(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, 0xFF2E3338);
        int knobX = value ? x + SWITCH_WIDTH - 12 : x + 2;
        graphics.fill(knobX, y + 2, knobX + 10, y + SWITCH_HEIGHT - 2, value ? Theme.GOOD : 0xFF6A6F78);
        text(value ? "On" : "Off", x + SWITCH_WIDTH + 5, centerY - 4, value ? Theme.TEXT : Theme.MUTED);
        return clicked(x, centerY - 9, SWITCH_WIDTH + 30, 18) != value;
    }

    /** A row of text choices, with the chosen one framed. Returns the new choice. */
    public <T> T segments(int x, int centerY, List<T> values, T value, Function<T, String> name) {
        int y = centerY - SLOT / 2;
        T result = value;
        for (T candidate : values) {
            String label = name.apply(candidate);
            int width = width(label) + 2 * SEGMENT_PADDING;
            boolean selected = candidate.equals(value) || (candidate == value);
            boolean hover = hovered(x, y, width, SLOT);
            graphics.fill(x, y, x + width, y + SLOT, hover ? Theme.SELECTED : Theme.SLOT);
            if (selected) {
                frame(x, y, width, SLOT);
            }
            text(label, x + SEGMENT_PADDING, centerY - 4, selected ? 0xFFFFFFFF : Theme.MUTED);
            if (clicked(x, y, width, SLOT)) {
                result = candidate;
            }
            x += width + 1;
        }
        return result;
    }

    public float slider(Object id, int x, int centerY, int trackWidth, float value, float min, float max, String label) {
        int y = centerY - 3;
        if (clicked(x, centerY - 8, trackWidth, 16)) {
            dragging = id;
        }
        float result = value;
        if (isDragging(id)) {
            result = min + (max - min) * Math.clamp((float) (mouseX - x) / trackWidth, 0, 1);
        }

        float shown = (result - min) / (max - min);
        int knob = x + Math.round(shown * (trackWidth - 6));
        graphics.fill(x, y, x + trackWidth, y + 6, Theme.ROW);
        graphics.fill(x + 1, y + 1, knob, y + 5, Theme.ACCENT);
        graphics.fill(knob, centerY - 7, knob + 6, centerY + 7, 0xFFCFCFCF);
        text(label, x + trackWidth + 5, centerY - 4, Theme.MUTED);
        return result;
    }

    /** Every tier as a cell. Tiers that would be hidden are dimmed. `All` means no minimum. */
    public Tier tierPicker(int x, int centerY, Tier value, ToIntFunction<Tier> color) {
        int y = centerY - SLOT / 2;
        Tier result = value;

        boolean none = value == null;
        graphics.fill(x, y, x + TIER_CELL, y + SLOT, Theme.SLOT);
        if (none) {
            frame(x, y, TIER_CELL, SLOT);
        }
        graphics.centeredText(font, "All", x + TIER_CELL / 2, centerY - 4, none ? 0xFFFFFFFF : Theme.MUTED);
        if (clicked(x, y, TIER_CELL, SLOT)) {
            result = null;
        }
        x += TIER_CELL + 1;

        for (Tier tier : Tier.STANDARD) {
            boolean included = value == null || tier.isAtLeast(value);
            boolean selected = tier.equals(value);
            boolean hover = hovered(x, y, TIER_CELL, SLOT);
            graphics.fill(x, y, x + TIER_CELL, y + SLOT, hover ? Theme.SELECTED : Theme.SLOT);
            if (selected) {
                frame(x, y, TIER_CELL, SLOT);
            }
            int rgb = color.applyAsInt(tier);
            int textColor = included ? Theme.opaque(rgb) : Theme.withAlpha(rgb, 0.3f);
            graphics.centeredText(font, tier.label(), x + TIER_CELL / 2, centerY - 4, textColor);
            if (clicked(x, y, TIER_CELL, SLOT)) {
                result = tier;
            }
            x += TIER_CELL + 1;
        }
        return result;
    }

    /** Every tier as a tab. Picks which tier the sliders below are editing. */
    public Tier tierTabs(int x, int centerY, Tier value, ToIntFunction<Tier> color) {
        int y = centerY - SLOT / 2;
        Tier result = value;
        for (Tier tier : Tier.STANDARD) {
            boolean selected = tier.equals(value);
            graphics.fill(x, y, x + TIER_CELL, y + SLOT, hovered(x, y, TIER_CELL, SLOT) ? Theme.SELECTED : Theme.SLOT);
            if (selected) {
                frame(x, y, TIER_CELL, SLOT);
            }
            graphics.centeredText(font, tier.label(), x + TIER_CELL / 2, centerY - 4, Theme.opaque(color.applyAsInt(tier)));
            if (clicked(x, y, TIER_CELL, SLOT)) {
                result = tier;
            }
            x += TIER_CELL + 1;
        }
        return result;
    }

    /** One tier in a cell. Clicking steps through the tiers, and right clicking steps back. */
    public Tier cycleTier(int x, int centerY, Tier value, ToIntFunction<Tier> color) {
        int y = centerY - SLOT / 2;
        boolean hover = hovered(x, y, TIER_CELL + 8, SLOT);
        graphics.fill(x, y, x + TIER_CELL + 8, y + SLOT, hover ? Theme.SELECTED : Theme.SLOT);
        graphics.centeredText(font, value.label(), x + (TIER_CELL + 8) / 2, centerY - 4, Theme.opaque(color.applyAsInt(value)));
        List<Tier> tiers = Tier.STANDARD;
        int index = Math.max(0, tiers.indexOf(value));
        if (clicked(x, y, TIER_CELL + 8, SLOT)) {
            return tiers.get((index + 1) % tiers.size());
        }
        if (rightClicked(x, y, TIER_CELL + 8, SLOT)) {
            return tiers.get((index + tiers.size() - 1) % tiers.size());
        }
        return value;
    }

    /** A block of colour. Returns true when clicked. */
    public boolean swatch(int x, int centerY, int rgb, boolean selected) {
        int y = centerY - SLOT / 2;
        graphics.fill(x, y, x + SLOT, y + SLOT, Theme.opaque(rgb));
        if (selected) {
            frame(x, y, SLOT, SLOT);
        } else if (hovered(x, y, SLOT, SLOT)) {
            graphics.outline(x, y, SLOT, SLOT, 0xFFFFFFFF);
        }
        return clicked(x, y, SLOT, SLOT);
    }
}
