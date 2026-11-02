package dev.tierprofiles.ui;

import dev.tierprofiles.core.settings.Colors;
import java.util.function.IntConsumer;

/** A small popup with red, green and blue sliders. */
final class ColorPicker {
    private static final int WIDTH = 190;
    private static final int HEIGHT = 92;

    private final IntConsumer onPick;
    private final int original;
    private final Object redId = new Object();
    private final Object greenId = new Object();
    private final Object blueId = new Object();
    private float red;
    private float green;
    private float blue;

    ColorPicker(int rgb, IntConsumer onPick) {
        this.original = rgb;
        this.onPick = onPick;
        red = rgb >> 16 & 0xFF;
        green = rgb >> 8 & 0xFF;
        blue = rgb & 0xFF;
    }

    int color() {
        return Math.round(red) << 16 | Math.round(green) << 8 | Math.round(blue);
    }

    /** Returns true when the popup should close. */
    boolean draw(Ui ui, int screenWidth, int screenHeight) {
        int x = (screenWidth - WIDTH) / 2;
        int y = (screenHeight - HEIGHT) / 2;
        ui.graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);
        ui.panel(x, y, WIDTH, HEIGHT);

        ui.setBlocked(false);
        int right = x + WIDTH - 6;
        ui.text("Pick a colour", x + 6, y + 6, Theme.TEXT);
        ui.swatch(right - 44, y + 4, 12, original);
        ui.swatch(right - 30, y + 4, 12, color());
        ui.text(Colors.toHex(color()).substring(1), right - 16 - 16, y + 18, Theme.MUTED);

        red = row(ui, "R", redId, red, right, y + 32);
        green = row(ui, "G", greenId, green, right, y + 46);
        blue = row(ui, "B", blueId, blue, right, y + 60);

        boolean close = false;
        if (ui.button(right - 56, y + HEIGHT - 18, 56, 14, "Use", true)) {
            onPick.accept(color());
            close = true;
        }
        if (ui.button(right - 112, y + HEIGHT - 18, 52, 14, "Cancel", false)) {
            close = true;
        }
        ui.consumeClick();
        ui.setBlocked(true);
        return close;
    }

    private static float row(Ui ui, String label, Object id, float value, int right, int y) {
        ui.text(label, right - WIDTH + 12, y - 4, Theme.MUTED);
        return ui.slider(id, right, y, WIDTH - 32, value, 0, 255, Integer.toString(Math.round(value)));
    }
}
