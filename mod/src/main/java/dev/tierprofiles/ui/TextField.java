package dev.tierprofiles.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * A plain one-line text box that draws through Ui, so it scales with the rest of the
 * screen. Typing always happens at the end of the text, which is all these boxes need.
 */
final class TextField {
    private static final long BLINK_MILLIS = 500;

    private final int maxLength;
    private final String hint;
    String value = "";
    boolean focused;

    TextField(int maxLength, String hint) {
        this.maxLength = maxLength;
        this.hint = hint;
    }

    void draw(Ui ui, int x, int y, int width, int height) {
        ui.graphics.fill(x, y, x + width, y + height, Theme.ROW);
        ui.graphics.outline(x, y, width, height, focused ? Theme.ACCENT : 0xFF2E3338);
        drawBare(ui, x, y, width, height);
    }

    /** Text and caret only, for a box that sits on something already drawn. */
    void drawBare(Ui ui, int x, int y, int width, int height) {
        if (ui.clicked(x, y, width, height)) {
            focused = true;
        } else if (ui.anyClick() && !ui.hovered(x, y, width, height)) {
            focused = false;
        }

        int textY = y + (height - 8) / 2;
        if (value.isEmpty() && !focused) {
            ui.text(hint, x + 5, textY, Theme.MUTED);
            return;
        }
        String shown = ui.font.plainSubstrByWidth(value, width - 12, true);
        ui.text(shown, x + 5, textY, 0xFFFFFFFF);
        if (focused && System.currentTimeMillis() / BLINK_MILLIS % 2 == 0) {
            int caret = x + 5 + ui.width(shown);
            ui.graphics.fill(caret, textY - 1, caret + 1, textY + 9, 0xFFFFFFFF);
        }
    }

    boolean onChar(CharacterEvent event) {
        if (!focused || !event.isAllowedChatCharacter()) {
            return false;
        }
        if (value.length() < maxLength) {
            value += event.codepointAsString();
        }
        return true;
    }

    boolean onKey(KeyEvent event) {
        if (!focused) {
            return false;
        }
        if (event.input() == InputConstants.KEY_BACKSPACE) {
            if (!value.isEmpty()) {
                value = value.substring(0, value.length() - 1);
            }
            return true;
        }
        if (event.isPaste()) {
            String pasted = Minecraft.getInstance().keyboardHandler.getClipboard().strip();
            value = (value + pasted).substring(0, Math.min(maxLength, value.length() + pasted.length()));
            return true;
        }
        return false;
    }
}
