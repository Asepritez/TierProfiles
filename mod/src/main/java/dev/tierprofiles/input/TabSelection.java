package dev.tierprofiles.input;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.anim.Smoothed;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.hud.CardState;
import dev.tierprofiles.mixin.PlayerTabOverlayAccessor;
import dev.tierprofiles.ui.Theme;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** Which tab list row is selected, and how arrow keys move that selection. */
public final class TabSelection {
    private static final int MAX_ROWS = 20;
    private static final int RIGHT_PADDING = 3;
    private static final int HEAD_WIDTH = 10;
    private static final float SLIDE_HALF_LIFE_MILLIS = 28;

    /** -1 until the first arrow press, so the list looks normal until you use it. */
    private static int index = -1;

    private static PlayerInfo selected;
    private static Component selectedText;
    private static int bottomOfRows;
    private static int rightOfRows;

    private static final Smoothed slideX = new Smoothed(SLIDE_HALF_LIFE_MILLIS);
    private static final Smoothed slideY = new Smoothed(SLIDE_HALF_LIFE_MILLIS);
    private static final Smoothed slideWidth = new Smoothed(SLIDE_HALF_LIFE_MILLIS);
    private static boolean hasTarget;
    private static int targetX;
    private static int targetY;
    private static int targetWidth;
    private static boolean highlightDrawn;

    private TabSelection() {
    }

    public static boolean isListVisible() {
        return accessor().tierprofiles$isVisible();
    }

    private static PlayerTabOverlayAccessor accessor() {
        return (PlayerTabOverlayAccessor) Minecraft.getInstance().gui.hud.getTabList();
    }

    public static void clear() {
        index = -1;
        selected = null;
        selectedText = null;
        hasTarget = false;
        slideX.reset();
        slideY.reset();
        slideWidth.reset();
    }

    /** Arrow keys: one row up or down, or one column across. */
    public static void move(int rowsDown, int columnsAcross) {
        List<PlayerInfo> players = accessor().tierprofiles$players();
        if (players.isEmpty()) {
            return;
        }

        if (index < 0) {
            index = 0;
        } else {
            int step = rowsDown + columnsAcross * rowsPerColumn(players.size());
            index = Math.clamp(index + step, 0, players.size() - 1);
        }
        selected = players.get(index);
    }

    /** The same sum the game uses to lay the list out. */
    static int rowsPerColumn(int players) {
        int rows = players;
        int columns = 1;
        while (rows > MAX_ROWS) {
            columns += 1;
            rows = (players + columns - 1) / columns;
        }
        return rows;
    }

    public static PlayerInfo selected() {
        return selected;
    }

    public static void remember(PlayerInfo info, Component text) {
        if (info == selected) {
            selectedText = text;
        }
    }

    public static void beforeRow(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y) {
        bottomOfRows = Math.max(bottomOfRows, y + font.lineHeight);
        rightOfRows = Math.max(rightOfRows, x + font.width(text));
        if (text == selectedText && text != null) {
            targetX = x - HEAD_WIDTH;
            targetY = y - 1;
            targetWidth = font.width(text) + HEAD_WIDTH + RIGHT_PADDING;
            hasTarget = true;
        }
        if (!highlightDrawn && hasTarget) {
            highlightDrawn = true;
            drawHighlight(graphics);
        }
    }

    /**
     * Drawn once a frame, before the row text, using where the selected row was
     * last frame. The highlight glides there instead of jumping between rows.
     */
    private static void drawHighlight(GuiGraphicsExtractor graphics) {
        long now = CardState.now();
        CardSettings card = TierProfiles.settings().card;
        if (card.effectiveStyle(TierProfiles.config().global.reduceMotion) == CardSettings.AnimationStyle.OFF) {
            slideX.snap(targetX, now);
            slideY.snap(targetY, now);
            slideWidth.snap(targetWidth, now);
        }
        int x = Math.round(slideX.update(targetX, now));
        int y = Math.round(slideY.update(targetY, now));
        int width = Math.round(slideWidth.update(targetWidth, now));
        graphics.fill(x, y, x + width, y + 9, Theme.SELECTED);
        graphics.outline(x, y, width, 9, Theme.ACCENT);
    }

    public record Extent(int right, int bottom) {
    }

    /** How far the tab list reached this frame, and resets for the next one. */
    public static Extent takeExtent() {
        Extent extent = new Extent(rightOfRows, bottomOfRows);
        rightOfRows = 0;
        bottomOfRows = 0;
        highlightDrawn = false;
        return extent;
    }
}
