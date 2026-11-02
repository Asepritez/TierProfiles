package dev.tierprofiles.hud;

import dev.tierprofiles.core.model.Title;
import dev.tierprofiles.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The small mark before a title: a drawn diamond coloured by rank. Returns how much
 * width it took, so the title can follow it.
 */
final class TitleBadge {
    private static final int DIAMOND = 9;
    private static final int GAP = 3;
    /** The room a badge takes, including its gap. */
    static final int WIDTH = DIAMOND + GAP;

    private TitleBadge() {
    }

    private static int colorOf(Title title) {
        return switch (title) {
            case GRANDMASTER -> 0xFFCF4A;
            case MASTER -> 0xC58BFF;
            case ACE -> 0xFF5F8A;
            case SPECIALIST -> 0x68B5D5;
            case CADET -> 0x77D877;
            case NOVICE -> 0xD0D6E0;
            case ROOKIE -> 0x8A8F98;
        };
    }

    static int draw(GuiGraphicsExtractor graphics, Title title, int x, int y, float alpha) {
        int color = Theme.fade(Theme.opaque(colorOf(title)), alpha);
        int middle = DIAMOND / 2;
        for (int row = 0; row < DIAMOND; row++) {
            int inset = Math.abs(row - middle);
            graphics.fill(x + inset, y + row, x + DIAMOND - inset, y + row + 1, color);
        }
        int hole = Theme.fade(0xFF10141A, alpha);
        graphics.fill(x + middle - 1, y + middle, x + middle + 2, y + middle + 1, hole);
        graphics.fill(x + middle, y + middle - 1, x + middle + 1, y + middle + 2, hole);
        return DIAMOND + GAP;
    }
}
