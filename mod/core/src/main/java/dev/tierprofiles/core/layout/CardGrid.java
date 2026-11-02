package dev.tierprofiles.core.layout;

import dev.tierprofiles.core.model.Band;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.CardSettings.Detail;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The card's fixed column grid, from docs/LAYOUT.md. The numbers there are minimums.
 * Each column is also measured from the font, for the widest thing that can ever sit in
 * it, and the larger of the two wins. That's what makes overlap impossible rather than
 * merely unlikely, even with a resource pack that changes the font.
 *
 * All sizes are GUI pixels. Pure maths with no Minecraft, so tests can prove the columns
 * never collide.
 */
public final class CardGrid {
    public static final int PADDING = 6;
    public static final int GAP = 6;
    public static final int MODEL_X = PADDING;
    public static final int ICON = 16;
    public static final int STRIP = 32;
    public static final int ROWS_Y = 36;
    public static final int ROW = 19;
    public static final int SLOT_HEIGHT = 18;

    public static final int STANDARD_KITS = 3;
    public static final int FULL_KITS = 5;

    /** Space kept around the card, which docs/LAYOUT.md takes from the screen width. */
    public static final int SCREEN_ROOM = 8;

    private static final int MIN_MODEL = 60;
    private static final int LOGO_GAP = 4;
    private static final int MIN_NAME = 56;
    private static final int MIN_CHIP = 24;
    private static final int SLOT_INNER = 1 + ICON + 1;
    private static final int MIN_DATE = 62;
    private static final int MIN_FULL_SLOT = 46;
    private static final int FULL_ROW = 31;
    private static final int LEGEND = 13;
    private static final int MINIMAL_HEIGHT = 61;
    private static final int MINIMAL_STEP = 67;
    private static final int MINIMAL_LOGO_GAP = 3;
    private static final int MINIMAL_MAX_LISTS = 4;
    private static final int MIN_EFFECTIVE_SCALE = 2;

    public final int modelWidth;
    public final int listX;
    public final int nameX;
    public final int chipWidth;
    public final int slotWidth;
    public final int slotStep;
    public final int firstKitX;
    public final int dateWidth;
    public final int fullSlotWidth;
    public final int fullSlotStep;
    public final int minimalStep;

    /**
     * One way of laying out the card.
     *
     * @param kits   how many kit columns
     * @param date   whether the date column shows
     * @param lists  in Minimal, how many lists fit
     * @param model  whether the model column shows
     * @param scale  1 unless even the smallest layout was too big
     */
    public record Shape(Detail detail, int kits, boolean date, int lists, boolean model, float scale) {
    }

    public CardGrid(ToIntFunction<String> textWidth) {
        modelWidth = MIN_MODEL;
        listX = MODEL_X + modelWidth + GAP;
        nameX = listX + ICON + LOGO_GAP;

        int widestName = MIN_NAME;
        for (TierList list : TierList.values()) {
            widestName = Math.max(widestName, textWidth.applyAsInt(rowName(list)));
        }
        firstKitX = nameX + widestName + GAP;

        int widestTier = 0;
        for (Band band : Band.values()) {
            for (int level = Tier.MIN_LEVEL; level <= Tier.MAX_LEVEL; level++) {
                widestTier = Math.max(widestTier, textWidth.applyAsInt(Tier.of(band, level).label()));
            }
        }
        chipWidth = Math.max(MIN_CHIP, widestTier + 2);
        slotWidth = SLOT_INNER + chipWidth;
        slotStep = slotWidth + GAP;

        dateWidth = Math.max(MIN_DATE, widestDate(textWidth));

        int widestMonth = textWidth.applyAsInt("Aug 2026");
        fullSlotWidth = Math.max(Math.max(MIN_FULL_SLOT, widestMonth), slotWidth);
        fullSlotStep = fullSlotWidth + GAP;
        minimalStep = Math.max(MINIMAL_STEP, ICON + MINIMAL_LOGO_GAP + slotWidth + GAP);
    }

    /** What a row's name column says. */
    public static String rowName(TierList list) {
        return list.isBeta() ? list.displayName() + " beta" : list.displayName();
    }

    private static int widestDate(ToIntFunction<String> textWidth) {
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        int widest = 0;
        for (String month : months) {
            widest = Math.max(widest, textWidth.applyAsInt("28 " + month + " 2028"));
        }
        return widest;
    }

    public int kitX(int slot) {
        return firstKitX + slot * slotStep;
    }

    public int fullKitX(int slot) {
        return firstKitX + slot * fullSlotStep;
    }

    public int minimalX(Shape shape, int list) {
        return contentX(shape) + list * minimalStep;
    }

    /** Where text starts once the model column is accounted for. */
    public int contentX(Shape shape) {
        return shape.model() ? listX : PADDING;
    }

    public int minimalSlotX(Shape shape, int list) {
        return minimalX(shape, list) + ICON + MINIMAL_LOGO_GAP;
    }

    public int width(Shape shape) {
        return switch (shape.detail()) {
            case MINIMAL -> shape.model()
                    ? firstKitX + STANDARD_KITS * slotStep + dateWidth + PADDING
                    : PADDING + Math.max(1, shape.lists()) * minimalStep;
            case STANDARD -> firstKitX + shape.kits() * slotStep + (shape.date() ? dateWidth : -GAP) + PADDING;
            case FULL -> firstKitX + FULL_KITS * fullSlotStep - GAP + PADDING;
        };
    }

    public int height(Shape shape, int rows) {
        return switch (shape.detail()) {
            case MINIMAL -> MINIMAL_HEIGHT;
            case STANDARD -> ROWS_Y + Math.max(1, rows) * ROW + PADDING;
            case FULL -> ROWS_Y + Math.max(1, rows) * FULL_ROW + LEGEND + PADDING;
        };
    }

    /** The strip's rank is on the Kit 3 column in Standard, and moves in tighter layouts. */
    public int rankX(Shape shape) {
        return switch (shape.detail()) {
            case FULL -> rightEdge(shape) - 116;
            case MINIMAL -> shape.model() ? kitX(2) : rightEdge(shape) - 36;
            case STANDARD -> {
                if (shape.date()) {
                    yield kitX(2);
                }
                yield shape.kits() == STANDARD_KITS ? rightEdge(shape) - 100 : rightEdge(shape) - 36;
            }
        };
    }

    /** In the tightest layouts there's no room for the list's name beneath the rank. */
    public boolean showsRankList(Shape shape) {
        return shape.model() && !(shape.detail() == Detail.STANDARD && shape.kits() < STANDARD_KITS);
    }

    /** With no model, the strip carries only the name and rank. */
    public boolean showsTitle(Shape shape) {
        return shape.model();
    }

    public boolean showsPointsLabel(Shape shape) {
        return shape.model();
    }

    /** The moon sits in the strip between the name and the rank. */
    public int moonX(Shape shape) {
        return shape.detail() == Detail.FULL ? 268 : 226;
    }

    public int fullRow() {
        return FULL_ROW;
    }

    /** The right edge the top strip and date column share. */
    public int rightEdge(Shape shape) {
        return width(shape) - PADDING;
    }

    public static Shape standard() {
        return new Shape(Detail.STANDARD, STANDARD_KITS, true, 0, true, 1);
    }

    private Shape minimal(int availableWidth, int rankedLists, float scale) {
        int wanted = Math.clamp(rankedLists, 1, MINIMAL_MAX_LISTS);
        Shape withModel = new Shape(Detail.MINIMAL, 0, false, wanted, true, scale);
        if (width(withModel) * scale <= availableWidth) {
            return withModel;
        }
        for (int lists = wanted; lists > 1; lists--) {
            Shape shape = new Shape(Detail.MINIMAL, 0, false, lists, false, scale);
            if (width(shape) * scale <= availableWidth) {
                return shape;
            }
        }
        return new Shape(Detail.MINIMAL, 0, false, 1, false, scale);
    }

    /** The shapes to try, richest first, starting from what you asked for. */
    private List<Shape> shapesFrom(Detail wanted, float scale) {
        List<Shape> shapes = new ArrayList<>();
        if (wanted == Detail.FULL) {
            shapes.add(new Shape(Detail.FULL, FULL_KITS, true, 0, true, scale));
        }
        if (wanted != Detail.MINIMAL) {
            shapes.add(new Shape(Detail.STANDARD, STANDARD_KITS, true, 0, true, scale));
            shapes.add(new Shape(Detail.STANDARD, STANDARD_KITS, false, 0, true, scale));
            shapes.add(new Shape(Detail.STANDARD, STANDARD_KITS - 1, false, 0, true, scale));
        }
        return shapes;
    }

    /**
     * Sizes that stay sharp: your scale, then smaller ones where the GUI scale times the
     * card scale is still a whole number. At GUI 4, 0.75 draws exactly like GUI 3, so
     * the card stays crisp. It stops at 2, below which text gets hard to read.
     */
    private static List<Float> crispScales(float wanted, int guiScale) {
        List<Float> scales = new ArrayList<>();
        scales.add(wanted);
        for (int effective = guiScale - 1; effective >= MIN_EFFECTIVE_SCALE; effective--) {
            float candidate = (float) effective / guiScale;
            if (candidate < wanted) {
                scales.add(candidate);
            }
        }
        return scales;
    }

    /**
     * The richest layout that fits on screen and clear of the crosshair and hotbar. It
     * tries a smaller crisp size before dropping detail, so a small window loses
     * information before anything can overlap. Text only goes soft as a last resort.
     *
     * @param rows        list rows on the card
     * @param rankedLists lists with something to show
     * @param scale       your card scale setting
     * @param guiScale    the game's GUI scale
     * @param isClear     whether a shape, once placed, avoids the crosshair and hotbar
     */
    public Shape fit(Detail wanted, int rows, int rankedLists, float scale, int guiScale,
            int availableWidth, int availableHeight, Predicate<Shape> isClear) {
        for (Float candidate : crispScales(scale, guiScale)) {
            for (Shape shape : shapesFrom(wanted, candidate)) {
                if (fits(shape, rows, availableWidth, availableHeight) && isClear.test(shape)) {
                    return shape;
                }
            }
        }
        for (Float candidate : crispScales(scale, guiScale)) {
            Shape shape = minimal(availableWidth, rankedLists, candidate);
            if (fits(shape, rows, availableWidth, availableHeight) && isClear.test(shape)) {
                return shape;
            }
        }

        Shape smallest = minimal(availableWidth, rankedLists, scale);
        float widest = (float) availableWidth / width(smallest);
        float tallest = (float) availableHeight / height(smallest, rows);
        float shrunk = Math.min(scale, Math.min(widest, tallest));
        return new Shape(Detail.MINIMAL, 0, false, smallest.lists(), smallest.model(), shrunk);
    }

    /** Fitting with nothing else to avoid, as when working out what a size allows. */
    public Shape fit(Detail wanted, int rows, int rankedLists, float scale, int availableWidth, int availableHeight) {
        return fit(wanted, rows, rankedLists, scale, 1, availableWidth, availableHeight, shape -> true);
    }

    private boolean fits(Shape shape, int rows, int availableWidth, int availableHeight) {
        return width(shape) * shape.scale() <= availableWidth && height(shape, rows) * shape.scale() <= availableHeight;
    }
}
