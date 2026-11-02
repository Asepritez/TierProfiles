package dev.tierprofiles.ui;

import dev.tierprofiles.core.settings.Colors;
import java.util.function.IntConsumer;

/** A small popup with red, green and blue sliders. */
final class ColorPicker {
    private static final int WIDTH = 230;
    private static final int HEIGHT = 112;

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
        int left = x + 10;
        ui.text("Pick a colour", left, y + 8, Theme.TEXT);
        ui.text(Colors.toHex(color()), x + WIDTH - 104, y + 8, Theme.MUTED);
        ui.swatch(x + WIDTH - 54, y + 13, original, false);
        ui.swatch(x + WIDTH - 32, y + 13, color(), false);

        red = row(ui, "R", redId, red, left, y + 36);
        green = row(ui, "G", greenId, green, left, y + 54);
        blue = row(ui, "B", blueId, blue, left, y + 72);

        boolean close = false;
        if (ui.button(x + WIDTH - 66, y + HEIGHT - 24, 56, 16, "Use", true)) {
            onPick.accept(color());
            close = true;
        }
        if (ui.button(x + WIDTH - 128, y + HEIGHT - 24, 56, 16, "Cancel", false)) {
            close = true;
        }
        ui.consumeClick();
        ui.setBlocked(true);
        return close;
    }

    private static float row(Ui ui, String label, Object id, float value, int x, int y) {
        ui.text(label, x, y - 4, Theme.MUTED);
        return ui.slider(id, x + 14, y, WIDTH - 60, value, 0, 255, Integer.toString(Math.round(value)));
    }
}
