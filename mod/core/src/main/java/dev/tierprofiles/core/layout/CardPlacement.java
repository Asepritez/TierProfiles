package dev.tierprofiles.core.layout;

import dev.tierprofiles.core.settings.CardSettings.Anchor;

/**
 * Works out where a card sits. The player picks an anchor and offsets, and this keeps
 * the result on screen and off the crosshair and hotbar, whatever they chose. The zones
 * come from docs/LAYOUT.md.
 */
public final class CardPlacement {
    public static final int EDGE = 10;
    private static final int CROSSHAIR = 33;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 40;

    private CardPlacement() {
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean overlaps(Rect other) {
            return x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height;
        }

        public boolean contains(int pointX, int pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
    }

    /** The distance that moves a card fully off screen, which is where it glides from. */
    public record Glide(int dx, int dy) {
    }

    public static Rect place(Anchor anchor, int offsetX, int offsetY, int width, int height, int screenWidth, int screenHeight) {
        int x = switch (anchor.column()) {
            case 0 -> EDGE;
            case 1 -> (screenWidth - width) / 2;
            default -> screenWidth - width - EDGE;
        };
        int y = switch (anchor.row()) {
            case 0 -> EDGE;
            case 1 -> (screenHeight - height) / 2;
            default -> screenHeight - height - EDGE;
        };

        Rect placed = clamp(new Rect(x + offsetX, y + offsetY, width, height), screenWidth, screenHeight);
        placed = avoid(placed, crosshair(screenWidth, screenHeight), screenWidth, screenHeight);
        placed = avoid(placed, hotbar(screenWidth, screenHeight), screenWidth, screenHeight);
        return clamp(placed, screenWidth, screenHeight);
    }

    private static Rect clamp(Rect rect, int screenWidth, int screenHeight) {
        int x = Math.clamp(rect.x, 0, Math.max(0, screenWidth - rect.width));
        int y = Math.clamp(rect.y, 0, Math.max(0, screenHeight - rect.height));
        return new Rect(x, y, rect.width, rect.height);
    }

    public static Rect crosshair(int screenWidth, int screenHeight) {
        return new Rect((screenWidth - CROSSHAIR) / 2, (screenHeight - CROSSHAIR) / 2, CROSSHAIR, CROSSHAIR);
    }

    public static Rect hotbar(int screenWidth, int screenHeight) {
        return new Rect((screenWidth - HOTBAR_WIDTH) / 2, screenHeight - HOTBAR_HEIGHT, HOTBAR_WIDTH, HOTBAR_HEIGHT);
    }

    /** True when a rectangle is clear of the crosshair and hotbar. */
    public static boolean isClear(Rect rect, int screenWidth, int screenHeight) {
        return !rect.overlaps(crosshair(screenWidth, screenHeight)) && !rect.overlaps(hotbar(screenWidth, screenHeight));
    }

    /** Moves a rectangle the shortest way out of a zone, staying on screen if it can. */
    private static Rect avoid(Rect rect, Rect zone, int screenWidth, int screenHeight) {
        if (!rect.overlaps(zone)) {
            return rect;
        }
        int[][] choices = {
            {rect.x, zone.y - rect.height},
            {rect.x, zone.y + zone.height},
            {zone.x - rect.width, rect.y},
            {zone.x + zone.width, rect.y},
        };

        Rect best = rect;
        int shortest = Integer.MAX_VALUE;
        for (int[] choice : choices) {
            boolean onScreen = choice[0] >= 0 && choice[1] >= 0
                    && choice[0] + rect.width <= screenWidth && choice[1] + rect.height <= screenHeight;
            int distance = Math.abs(choice[0] - rect.x) + Math.abs(choice[1] - rect.y);
            if (onScreen && distance < shortest) {
                shortest = distance;
                best = new Rect(choice[0], choice[1], rect.width, rect.height);
            }
        }
        return best;
    }

    /**
     * Corners and side anchors glide in from the left or right, top and bottom centre
     * from their own edge, and the centre anchor from the top.
     */
    public static Glide glide(Anchor anchor, Rect rect, int screenWidth, int screenHeight) {
        int toLeft = -(rect.x + rect.width);
        int toRight = screenWidth - rect.x;
        int toTop = -(rect.y + rect.height);
        int toBottom = screenHeight - rect.y;

        return switch (anchor.column()) {
            case 0 -> new Glide(toLeft, 0);
            case 2 -> new Glide(toRight, 0);
            default -> anchor.row() == 2 ? new Glide(0, toBottom) : new Glide(0, toTop);
        };
    }

    /**
     * The anchor and offsets for a card dragged to a spot. It snaps to whichever anchor
     * point is nearest, and what's left over becomes the offset.
     */
    public static Dragged drag(int x, int y, int width, int height, int screenWidth, int screenHeight, int snap) {
        Anchor best = Anchor.TOP_LEFT;
        int bestDistance = Integer.MAX_VALUE;
        for (Anchor anchor : Anchor.values()) {
            Rect rest = place(anchor, 0, 0, width, height, screenWidth, screenHeight);
            int distance = Math.abs(rest.x - x) + Math.abs(rest.y - y);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = anchor;
            }
        }
        Rect rest = rawAnchor(best, width, height, screenWidth, screenHeight);
        int offsetX = x - rest.x;
        int offsetY = y - rest.y;
        if (Math.abs(offsetX) <= snap) {
            offsetX = 0;
        }
        if (Math.abs(offsetY) <= snap) {
            offsetY = 0;
        }
        return new Dragged(best, offsetX, offsetY);
    }

    private static Rect rawAnchor(Anchor anchor, int width, int height, int screenWidth, int screenHeight) {
        int x = switch (anchor.column()) {
            case 0 -> EDGE;
            case 1 -> (screenWidth - width) / 2;
            default -> screenWidth - width - EDGE;
        };
        int y = switch (anchor.row()) {
            case 0 -> EDGE;
            case 1 -> (screenHeight - height) / 2;
            default -> screenHeight - height - EDGE;
        };
        return new Rect(x, y, width, height);
    }

    public record Dragged(Anchor anchor, int offsetX, int offsetY) {
    }
}
