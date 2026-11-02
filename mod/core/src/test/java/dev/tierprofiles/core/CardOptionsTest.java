package dev.tierprofiles.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.tierprofiles.core.layout.CardGrid;
import dev.tierprofiles.core.layout.CardGrid.Shape;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.CardSettings.Detail;
import dev.tierprofiles.core.settings.CardSettings.LastColumn;
import dev.tierprofiles.core.settings.Cosmetics;
import dev.tierprofiles.core.settings.Filters;
import dev.tierprofiles.core.settings.Settings;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Hiding lists and kits, the last column, All ranks, and per-piece trims. */
class CardOptionsTest {
    private static final ToIntFunction<String> FONT = text -> text.chars().map(c -> switch (c) {
        case 'i', '.', ',', ':', ';', '!', 'l', '\'' -> 3;
        case 't', 'I', ' ' -> 4;
        default -> 6;
    }).sum();

    private final CardGrid grid = new CardGrid(FONT);

    @Test
    void aFourthTierTakesTheDateColumnsPlace() {
        Shape four = CardGrid.standard(LastColumn.FOURTH_TIER);
        assertEquals(CardGrid.WIDE_KITS, four.kits());
        assertFalse(four.date());
        int lastEnd = grid.kitX(CardGrid.WIDE_KITS - 1) + grid.slotWidth;
        assertTrue(lastEnd <= grid.rightEdge(four), "the fourth kit runs off the card");
        assertTrue(grid.width(four) <= grid.width(CardGrid.standard()), "four tiers should be no wider than three and a date");
    }

    @ParameterizedTest
    @EnumSource(LastColumn.class)
    void theMoonAndRankNeverMeetWhateverFillsTheLastColumn(LastColumn last) {
        Shape shape = CardGrid.standard(last);
        assertTrue(grid.moonX(shape) + 18 < grid.rankX(shape));
        assertTrue(grid.rankX(shape) > grid.contentX(shape) + 56, "the name needs room before the rank");
    }

    @Test
    void theMoonStaysWhereTheDraftsPutIt() {
        CardGrid real = new CardGrid(text -> switch (text) {
            case "McPvP beta" -> 55;
            case "PvPTiers", "SubTiers" -> 43;
            case "Aug 2026" -> 45;
            default -> text.matches("\\d\\d \\w{3} \\d{4}") ? 61 : 17;
        });
        assertEquals(226, real.moonX(CardGrid.standard()));
        assertEquals(268, real.moonX(new Shape(Detail.FULL, CardGrid.FULL_KITS, true, 0, true, 1)));
    }

    @Test
    void pickingFourTiersFallsBackToThreeWhenNarrow() {
        Shape roomy = grid.fit(Detail.STANDARD, LastColumn.FOURTH_TIER, false, shape -> 5, 5, 1, 1, 900, 500, shape -> true);
        assertEquals(CardGrid.WIDE_KITS, roomy.kits());
        int threeWide = grid.width(new Shape(Detail.STANDARD, 3, false, 0, true, 1));
        Shape tight = grid.fit(Detail.STANDARD, LastColumn.FOURTH_TIER, false, shape -> 5, 5, 1, 1, threeWide, 500, shape -> true);
        assertEquals(3, tight.kits());
    }

    @Test
    void nothingInTheLastColumnMakesANarrowerCard() {
        Shape none = grid.fit(Detail.STANDARD, LastColumn.NONE, false, shape -> 5, 5, 1, 1, 900, 500, shape -> true);
        assertFalse(none.date());
        assertEquals(CardGrid.STANDARD_KITS, none.kits());
    }

    @Test
    void allRanksWrapsLongRowsOntoMoreLines() {
        Shape wide = new Shape(Detail.FULL, CardGrid.FULL_KITS, true, 0, true, 1, 8, true);
        assertEquals(1, CardGrid.linesFor(wide, 8));
        assertEquals(2, CardGrid.linesFor(wide, 11));
        assertEquals(1, CardGrid.linesFor(wide, 0), "an unranked row still takes a line");
        assertEquals(4, CardGrid.linesFor(wide, new int[] {11, 3, 0}));
        Shape plain = new Shape(Detail.FULL, CardGrid.FULL_KITS, true, 0, true, 1);
        assertEquals(1, CardGrid.linesFor(plain, 11), "without All ranks a row shows its best five");
    }

    @ParameterizedTest
    @CsvSource({"854, 480", "1280, 720", "1920, 1080"})
    void allRanksAlwaysFitsOnScreen(int width, int height) {
        int[] kits = {12, 9, 10, 8, 3};
        for (int guiScale : new int[] {2, 3, 4}) {
            int guiWidth = width / guiScale - CardGrid.SCREEN_ROOM;
            int guiHeight = height / guiScale - CardGrid.SCREEN_ROOM;
            Shape shape = grid.fit(Detail.STANDARD, LastColumn.DATE, true, s -> CardGrid.linesFor(s, kits), 5, 1, guiScale, guiWidth, guiHeight, s -> true);
            assertEquals(Detail.FULL, shape.detail(), "All ranks must never drop kits");
            assertTrue(shape.wraps());
            assertTrue(grid.width(shape) * shape.scale() <= guiWidth + 1, "too wide at " + width + " GUI " + guiScale);
            assertTrue(grid.height(shape, CardGrid.linesFor(shape, kits)) * shape.scale() <= guiHeight + 1, "too tall at " + width + " GUI " + guiScale);
        }
    }

    @Test
    void hiddenKitsSurviveSavingAndJunkIsDropped() {
        Settings settings = new Settings();
        settings.filters.hiddenKits.addAll(List.of("smp", "uhc", "<b>", ""));
        Settings back = new Gson().fromJson(new Gson().toJson(settings), Settings.class).sanitize();
        assertEquals(Set.of("smp", "uhc"), back.filters.hiddenKits);
        assertFalse(back.filters.isKitShown("smp"));
        assertTrue(back.filters.isKitShown("sword"));
    }

    @Test
    void anOwnersHiddenListsAddToYoursWithoutChangingYours() {
        Filters mine = new Filters();
        mine.disabledLists.add(TierList.MCPVP);
        Filters view = mine.plusHidden(Set.of(TierList.PVPTIERS), List.of("smp"));
        assertFalse(view.isEnabled(TierList.MCPVP));
        assertFalse(view.isEnabled(TierList.PVPTIERS));
        assertFalse(view.isKitShown("smp"));
        assertTrue(mine.isEnabled(TierList.PVPTIERS), "your own filters must stay as they were");
        assertTrue(mine.isKitShown("smp"));
    }

    @Test
    void anOlderCardGivesEveryPieceItsSharedTrim() {
        Cosmetics old = new Gson().fromJson("{\"trimPattern\":\"rib\",\"trimMaterial\":\"gold\"}", Cosmetics.class).sanitize();
        for (int slot = 0; slot < 4; slot++) {
            assertEquals("rib", old.trim(slot));
            assertEquals("gold", old.trimMaterial(slot));
        }
    }

    @Test
    void eachPieceKeepsItsOwnTrim() {
        Cosmetics look = new Cosmetics().sanitize();
        look.setTrim(0, "silence");
        look.setTrimMaterial(0, "gold");
        look.setTrim(1, "rib");
        Cosmetics back = new Gson().fromJson(new Gson().toJson(look), Cosmetics.class).sanitize();
        assertEquals("silence", back.trim(0));
        assertEquals("gold", back.trimMaterial(0));
        assertEquals("rib", back.trim(1));
        assertEquals("none", back.trim(2));
        assertEquals("rib", back.trimPattern, "older clients see the chestplate's trim");
    }

    @Test
    void olderConfigsLoseTheLetterBadgesOnTheAimCard() {
        String old = "{\"version\":1,\"profiles\":{\"Default\":{\"surfaces\":{\"LOOK_AT\":{\"listLogo\":true,\"enabled\":true}}}}}";
        dev.tierprofiles.core.settings.ConfigFile config = new Gson().fromJson(old, dev.tierprofiles.core.settings.ConfigFile.class).sanitize();
        assertFalse(config.active().surface(dev.tierprofiles.core.settings.Surface.LOOK_AT).listLogo);
        assertEquals(dev.tierprofiles.core.settings.ConfigFile.CURRENT_VERSION, config.version);

        // Someone who turns them back on afterwards keeps them.
        config.active().surface(dev.tierprofiles.core.settings.Surface.LOOK_AT).listLogo = true;
        assertTrue(new Gson().fromJson(new Gson().toJson(config), dev.tierprofiles.core.settings.ConfigFile.class).sanitize()
                .active().surface(dev.tierprofiles.core.settings.Surface.LOOK_AT).listLogo);
    }

    @Test
    void resettingKeepsOnlyThePrivacyAnswer() {
        dev.tierprofiles.core.settings.ConfigFile config = new dev.tierprofiles.core.settings.ConfigFile();
        config.global.noticeSeen = true;
        config.global.lookupsEnabled = false;
        config.global.cosmetics.background = "moon";
        config.active().filters.hiddenKits.add("smp");
        config.active().card.lastColumn = LastColumn.FOURTH_TIER;

        dev.tierprofiles.core.settings.ConfigFile fresh = dev.tierprofiles.core.settings.ConfigFile.defaultsKeepingConsent(config);
        assertTrue(fresh.global.noticeSeen, "the welcome page must not come back");
        assertFalse(fresh.global.lookupsEnabled, "a player who said no to lookups still has them off");
        assertEquals("plain", fresh.global.cosmetics.background);
        assertTrue(fresh.active().filters.hiddenKits.isEmpty());
        assertEquals(LastColumn.DATE, fresh.active().card.lastColumn);
    }

    @Test
    void hiddenOwnListsAreCheckedAgainstRealLists() {
        Cosmetics look = new Cosmetics();
        look.hiddenLists.addAll(List.of("pvptiers", "myserver", "pvptiers"));
        look.hiddenKits.addAll(List.of("smp", "DROP TABLE"));
        look.sanitize();
        assertEquals(List.of("pvptiers"), look.hiddenLists);
        assertEquals(Set.of(TierList.PVPTIERS), look.hiddenListSet());
        assertEquals(List.of("smp"), look.hiddenKits);
    }
}
