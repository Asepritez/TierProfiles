package dev.tierprofiles.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tierprofiles.core.layout.CardGrid;
import dev.tierprofiles.core.layout.CardGrid.Shape;
import dev.tierprofiles.core.layout.CardPlacement;
import dev.tierprofiles.core.layout.CardPlacement.Glide;
import dev.tierprofiles.core.layout.CardPlacement.Rect;
import dev.tierprofiles.core.model.Band;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.core.settings.CardSettings.Anchor;
import dev.tierprofiles.core.settings.CardSettings.Detail;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CardLayoutTest {
    /** Close to Minecraft's font: most characters are 6 wide, a few are narrower. */
    private static final ToIntFunction<String> FONT = text -> text.chars().map(c -> switch (c) {
        case 'i', '.', ',', ':', ';', '!', 'l', '\'' -> 3;
        case 't', 'I', ' ' -> 4;
        default -> 6;
    }).sum();

    private final CardGrid grid = new CardGrid(FONT);

    private static int[][] screens() {
        return new int[][] {{854, 480}, {1280, 720}, {1920, 1080}};
    }

    private static final Shape STANDARD = CardGrid.standard();

    /** The real font's widths, from docs/LAYOUT.md, for checking the published numbers. */
    private static final ToIntFunction<String> REAL = text -> switch (text) {
        case "McPvP beta" -> 55;
        case "PvPTiers", "SubTiers" -> 43;
        case "Aug 2026" -> 45;
        default -> text.matches("\\d\\d \\w{3} \\d{4}") ? 61 : 17;
    };

    @Test
    void matchesThePublishedGridWithTheRealFont() {
        CardGrid real = new CardGrid(REAL);
        assertEquals(72, real.listX);
        assertEquals(92, real.nameX);
        assertEquals(154, real.kitX(0));
        assertEquals(202, real.kitX(1));
        assertEquals(250, real.kitX(2));
        assertEquals(42, real.slotWidth);
        assertEquals(366, real.width(STANDARD));
        assertEquals(360, real.rightEdge(STANDARD));
        assertEquals(137, real.height(STANDARD, 5));
    }

    @Test
    void steppedLayoutsMatchThePublishedTable() {
        CardGrid real = new CardGrid(REAL);
        assertEquals(366, real.width(real.fit(Detail.STANDARD, 5, 5, 1f, 374, 400)));
        assertEquals(298, real.width(real.fit(Detail.STANDARD, 5, 5, 1f, 306, 400)));
        assertEquals(250, real.width(real.fit(Detail.STANDARD, 5, 5, 1f, 258, 400)));
        assertEquals(Detail.MINIMAL, real.fit(Detail.STANDARD, 5, 5, 1f, 249, 400).detail());
    }

    @ParameterizedTest
    @CsvSource({
        "427, STANDARD, 3, true",
        "426, STANDARD, 3, true",
        "320, STANDARD, 3, false",
        "284, STANDARD, 2, false",
    })
    void gameSizesPickTheRightLayout(int guiWidth, Detail detail, int kits, boolean date) {
        CardGrid real = new CardGrid(REAL);
        Shape shape = real.fit(Detail.STANDARD, 5, 5, 1f, guiWidth - CardGrid.SCREEN_ROOM, 400);
        assertEquals(detail, shape.detail());
        assertEquals(kits, shape.kits());
        assertEquals(date, shape.date());
    }

    @Test
    void rankFollowsTheLayout() {
        CardGrid real = new CardGrid(REAL);
        assertEquals(250, real.rankX(STANDARD));
        assertEquals(366, real.width(new Shape(Detail.MINIMAL, 0, false, 4, true, 1)));
        Shape noDate = real.fit(Detail.STANDARD, 5, 5, 1f, 320, 400);
        assertEquals(real.rightEdge(noDate) - 100, real.rankX(noDate));
        Shape tight = real.fit(Detail.STANDARD, 5, 5, 1f, 270, 400);
        assertFalse(real.showsRankList(tight));
    }

    @Test
    void theWidestTierCentresInsideItsChip() {
        for (Band band : Band.values()) {
            for (int level = Tier.MIN_LEVEL; level <= Tier.MAX_LEVEL; level++) {
                assertTrue(FONT.applyAsInt(Tier.of(band, level).label()) <= grid.chipWidth);
            }
        }
    }

    @Test
    void theLongestListNameStopsBeforeTheFirstKit() {
        for (TierList list : TierList.values()) {
            int end = grid.nameX + FONT.applyAsInt(CardGrid.rowName(list));
            assertTrue(end + CardGrid.GAP <= grid.firstKitX, list + " touches the first kit");
        }
    }

    @Test
    void theLogoSlotEndsBeforeTheName() {
        assertTrue(grid.listX + CardGrid.ICON < grid.nameX);
    }

    @Test
    void theLastKitStopsBeforeTheDateColumn() {
        int lastEnd = grid.kitX(CardGrid.STANDARD_KITS - 1) + grid.slotWidth;
        int dateStart = grid.rightEdge(STANDARD) - grid.dateWidth;
        assertTrue(lastEnd + CardGrid.GAP <= dateStart, lastEnd + " > " + dateStart);
    }

    @Test
    void everyDateFitsTheDateColumn() {
        for (String month : new String[] {"Jan", "Mar", "May", "Aug", "Nov", "Dec"}) {
            assertTrue(FONT.applyAsInt("28 " + month + " 2026") <= grid.dateWidth);
        }
    }

    @Test
    void kitColumnsAreEvenlySpaced() {
        assertEquals(grid.slotStep, grid.kitX(1) - grid.kitX(0));
        assertEquals(grid.slotStep, grid.kitX(2) - grid.kitX(1));
        assertTrue(grid.slotStep >= grid.slotWidth + CardGrid.GAP);
    }

    @Test
    void aWiderFontWidensTheColumnsInsteadOfOverlapping() {
        ToIntFunction<String> wideFont = text -> text.length() * 12;
        CardGrid wide = new CardGrid(wideFont);
        assertTrue(wide.chipWidth >= wideFont.applyAsInt("HT3"));
        assertTrue(wide.firstKitX >= wide.nameX + wideFont.applyAsInt("McPvP beta"));
        assertTrue(wide.dateWidth >= wideFont.applyAsInt("28 May 2026"));
    }

    @ParameterizedTest
    @CsvSource({"854, 480", "1280, 720", "1920, 1080"})
    void everyScreenAndGuiScaleGetsAFit(int width, int height) {
        for (int guiScale : new int[] {2, 3, 4}) {
            int guiWidth = width / guiScale - CardGrid.SCREEN_ROOM;
            int guiHeight = height / guiScale - CardGrid.SCREEN_ROOM;
            for (Detail detail : Detail.values()) {
                Shape shape = grid.fit(detail, 5, 5, 1f, guiWidth, guiHeight);
                assertTrue(grid.width(shape) * shape.scale() <= guiWidth + 1, "too wide at " + width + " scale " + guiScale);
                assertTrue(grid.height(shape, 5) * shape.scale() <= guiHeight + 1, "too tall at " + width + " scale " + guiScale);
                assertTrue(shape.detail().ordinal() <= detail.ordinal(), "fitting must never add detail");
            }
        }
    }

    @Test
    void roomyScreensKeepWhatYouAskedFor() {
        Shape shape = grid.fit(Detail.STANDARD, 5, 5, 1f, 900, 500);
        assertEquals(Detail.STANDARD, shape.detail());
        assertEquals(1f, shape.scale());
        assertEquals(Detail.FULL, grid.fit(Detail.FULL, 5, 5, 1f, 900, 500).detail());
    }

    @Test
    void tightScreensStepDownRatherThanShrink() {
        Shape shape = grid.fit(Detail.FULL, 5, 5, 1f, 230, 200);
        assertEquals(Detail.MINIMAL, shape.detail());
        assertEquals(1f, shape.scale());
    }

    @Test
    void minimalDropsListsThenTheModelBeforeItShrinks() {
        Shape two = grid.fit(Detail.MINIMAL, 5, 4, 1f, 205, 200);
        assertEquals(1f, two.scale());
        assertTrue(grid.width(two) <= 205);
    }

    private Shape fitFor(CardGrid grid, int guiWidth, int guiHeight, int guiScale) {
        return grid.fit(Detail.STANDARD, 5, 5, 1f, guiScale, guiWidth - CardGrid.SCREEN_ROOM, guiHeight - CardGrid.SCREEN_ROOM, shape -> {
            int width = Math.round(grid.width(shape) * shape.scale());
            int height = Math.round(grid.height(shape, 5) * shape.scale());
            Rect rect = CardPlacement.place(Anchor.TOP_RIGHT, 0, 0, width, height, guiWidth, guiHeight);
            return CardPlacement.isClear(rect, guiWidth, guiHeight);
        });
    }

    @Test
    void defaultGuiScaleAt1080pKeepsTheFullCardAtACrispSize() {
        CardGrid real = new CardGrid(REAL);
        Shape shape = fitFor(real, 480, 270, 4);
        assertEquals(Detail.STANDARD, shape.detail());
        assertEquals(CardGrid.STANDARD_KITS, shape.kits());
        assertTrue(shape.date());
        assertEquals(0.75f, shape.scale());
    }

    @Test
    void roomyGuiSizesAreNotShrunk() {
        CardGrid real = new CardGrid(REAL);
        assertEquals(1f, fitFor(real, 960, 540, 2).scale());
        assertEquals(1f, fitFor(real, 640, 360, 3).scale());
        assertEquals(1f, fitFor(real, 640, 360, 2).scale());
    }

    @ParameterizedTest
    @CsvSource({"854, 480", "1280, 720", "1920, 1080"})
    void defaultPlacementNeverCoversTheCrosshairAtAnySize(int width, int height) {
        CardGrid real = new CardGrid(REAL);
        for (int guiScale : new int[] {2, 3, 4}) {
            int guiWidth = width / guiScale;
            int guiHeight = height / guiScale;
            Shape shape = fitFor(real, guiWidth, guiHeight, guiScale);
            int cardWidth = Math.round(real.width(shape) * shape.scale());
            int cardHeight = Math.round(real.height(shape, 5) * shape.scale());
            Rect rect = CardPlacement.place(Anchor.TOP_RIGHT, 0, 0, cardWidth, cardHeight, guiWidth, guiHeight);
            assertTrue(CardPlacement.isClear(rect, guiWidth, guiHeight), width + " at GUI " + guiScale + " covers the crosshair");
            float effective = shape.scale() * guiScale;
            assertEquals(Math.round(effective), effective, 0.001f, "text should stay sharp at " + width + " GUI " + guiScale);
            assertTrue(effective >= 2, "text would be tiny at " + width + " GUI " + guiScale);
        }
    }

    @Test
    void onlyAVeryTinySpaceShrinksText() {
        Shape shape = grid.fit(Detail.STANDARD, 5, 5, 1f, 40, 200);
        assertEquals(Detail.MINIMAL, shape.detail());
        assertTrue(shape.scale() < 1);
    }

    @ParameterizedTest
    @EnumSource(Anchor.class)
    void everyAnchorStaysOnEveryScreen(Anchor anchor) {
        for (int[] screen : screens()) {
            for (int guiScale : new int[] {2, 3, 4}) {
                int w = screen[0] / guiScale;
                int h = screen[1] / guiScale;
                Rect rect = CardPlacement.place(anchor, 0, 0, 180, 60, w, h);
                assertTrue(rect.x() >= 0 && rect.y() >= 0, anchor + " left the screen");
                assertTrue(rect.x() + rect.width() <= w && rect.y() + rect.height() <= h, anchor + " left the screen");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"-5000, 0", "5000, 0", "0, -5000", "0, 5000", "5000, 5000"})
    void hugeOffsetsAreClamped(int offsetX, int offsetY) {
        Rect rect = CardPlacement.place(Anchor.TOP_LEFT, offsetX, offsetY, 180, 60, 640, 360);
        assertTrue(rect.x() >= 0 && rect.y() >= 0);
        assertTrue(rect.x() + rect.width() <= 640 && rect.y() + rect.height() <= 360);
    }

    @Test
    void anchorsLandWhereTheySay() {
        assertEquals(new Rect(10, 10, 100, 50), CardPlacement.place(Anchor.TOP_LEFT, 0, 0, 100, 50, 640, 360));
        assertEquals(new Rect(530, 10, 100, 50), CardPlacement.place(Anchor.TOP_RIGHT, 0, 0, 100, 50, 640, 360));
        assertEquals(new Rect(530, 300, 100, 50), CardPlacement.place(Anchor.BOTTOM_RIGHT, 0, 0, 100, 50, 640, 360));
    }

    @Test
    void offsetsMoveTheCard() {
        assertEquals(new Rect(20, 30, 100, 50), CardPlacement.place(Anchor.TOP_LEFT, 10, 20, 100, 50, 640, 360));
    }

    @Test
    void theCentreAnchorNeverCoversTheCrosshair() {
        Rect rect = CardPlacement.place(Anchor.CENTER, 0, 0, 180, 60, 640, 360);
        int crossX = 320;
        int crossY = 180;
        boolean covers = crossX >= rect.x() && crossX < rect.x() + rect.width()
                && crossY >= rect.y() && crossY < rect.y() + rect.height();
        assertFalse(covers);
    }

    @Test
    void theBottomCentreAnchorNeverCoversTheHotbar() {
        Rect rect = CardPlacement.place(Anchor.BOTTOM_CENTER, 0, 0, 180, 60, 640, 360);
        assertTrue(CardPlacement.isClear(rect, 640, 360));
    }

    @Test
    void cornersAndSidesGlideInFromTheSide() {
        Rect right = new Rect(530, 10, 100, 50);
        assertEquals(new Glide(110, 0), CardPlacement.glide(Anchor.TOP_RIGHT, right, 640, 360));
        Rect left = new Rect(10, 150, 100, 50);
        assertEquals(new Glide(-110, 0), CardPlacement.glide(Anchor.MIDDLE_LEFT, left, 640, 360));
    }

    @Test
    void centreAnchorsGlideInFromTopOrBottom() {
        Rect top = new Rect(270, 10, 100, 50);
        assertEquals(new Glide(0, -60), CardPlacement.glide(Anchor.TOP_CENTER, top, 640, 360));
        assertTrue(CardPlacement.glide(Anchor.CENTER, top, 640, 360).dy() < 0);
        Rect bottom = new Rect(270, 300, 100, 50);
        assertEquals(new Glide(0, 60), CardPlacement.glide(Anchor.BOTTOM_CENTER, bottom, 640, 360));
    }

    @Test
    void draggingNearACornerSnapsToIt() {
        CardPlacement.Dragged dragged = CardPlacement.drag(527, 13, 100, 50, 640, 360, 6);
        assertEquals(Anchor.TOP_RIGHT, dragged.anchor());
        assertEquals(0, dragged.offsetX());
        assertEquals(0, dragged.offsetY());
    }

    @Test
    void draggingFurtherKeepsTheOffset() {
        CardPlacement.Dragged dragged = CardPlacement.drag(500, 40, 100, 50, 640, 360, 6);
        assertEquals(Anchor.TOP_RIGHT, dragged.anchor());
        assertEquals(-30, dragged.offsetX());
        assertEquals(30, dragged.offsetY());
    }

    @Test
    void aMissingAnchorIsRepaired() {
        CardSettings card = new dev.tierprofiles.core.settings.Settings().card;
        card.anchor = null;
        card.offsetX = 99999;
        new dev.tierprofiles.core.settings.Settings().sanitize();
        dev.tierprofiles.core.settings.Settings settings = new dev.tierprofiles.core.settings.Settings();
        settings.card = card;
        settings.sanitize();
        assertEquals(Anchor.TOP_RIGHT, settings.card.anchor);
        assertEquals(CardSettings.MAX_OFFSET, settings.card.offsetX);
    }
}
