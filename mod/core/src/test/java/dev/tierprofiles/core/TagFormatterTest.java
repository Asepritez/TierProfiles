package dev.tierprofiles.core;

import static dev.tierprofiles.core.Fixtures.settings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tierprofiles.core.logic.Segment;
import dev.tierprofiles.core.logic.Tag;
import dev.tierprofiles.core.logic.TagFormatter;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Colors;
import dev.tierprofiles.core.settings.Palette;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Style;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.core.settings.SurfaceSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

class TagFormatterTest {
    private static Tag tag(String tier) {
        return new Tag(TierList.PVPHQ, "sword", Tier.parse(tier), null, false, "EU");
    }

    private static Tag retired(String tier) {
        return new Tag(TierList.PVPHQ, "sword", Tier.parse(tier), null, true, "EU");
    }

    private final Settings settings = settings();
    private final SurfaceSettings surface = settings.surface(Surface.NAMETAG);
    private final Style style = settings.style;

    private String text(Tag... tags) {
        return TagFormatter.plainText(TagFormatter.format(List.of(tags), surface, style));
    }

    @Test
    void defaultsToSquareBrackets() {
        assertEquals("[HT3]", text(tag("HT3")));
    }

    @Test
    void honoursEveryBracketStyle() {
        style.brackets = Style.Brackets.NONE;
        assertEquals("HT3", text(tag("HT3")));
        style.brackets = Style.Brackets.ROUND;
        assertEquals("(HT3)", text(tag("HT3")));
        style.brackets = Style.Brackets.ANGLE;
        assertEquals("<HT3>", text(tag("HT3")));
        style.brackets = Style.Brackets.CURLY;
        assertEquals("{HT3}", text(tag("HT3")));
        style.brackets = Style.Brackets.BARS;
        assertEquals("|HT3|", text(tag("HT3")));
    }

    @Test
    void longNames() {
        style.longNames = true;
        assertEquals("[High Tier 3]", text(tag("HT3")));
    }

    @Test
    void separatorSitsBetweenTagsOnly() {
        style.brackets = Style.Brackets.NONE;
        style.separator = Style.Separator.PIPE;
        assertEquals("HT3 | LT4", text(tag("HT3"), tag("LT4")));
    }

    @Test
    void regionIsOptional() {
        assertEquals("[HT3]", text(tag("HT3")));
        surface.region = true;
        assertEquals("[HT3 EU]", text(tag("HT3")));
    }

    @Test
    void regionIsLeftOutWhenUnknown() {
        surface.region = true;
        assertEquals("[HT3]", text(new Tag(TierList.SUBTIERS, "sword", Tier.parse("HT3"), null, false, null)));
    }

    @Test
    void iconsAreOptionalAndIndependent() {
        surface.kitIcon = true;
        surface.listLogo = true;
        List<Segment> both = TagFormatter.format(List.of(tag("HT3")), surface, style);
        assertTrue(both.contains(new Segment(Segment.Kind.KIT_ICON, "sword", 0xFFFFFF)));
        assertTrue(both.contains(new Segment(Segment.Kind.LIST_ICON, "pvphq", 0xFFFFFF)));

        surface.kitIcon = false;
        surface.listLogo = false;
        List<Segment> neither = TagFormatter.format(List.of(tag("HT3")), surface, style);
        assertTrue(neither.stream().allMatch(segment -> segment.kind() == Segment.Kind.TEXT));
    }

    @Test
    void theTierTextHasItsTierColour() {
        Segment tier = TagFormatter.format(List.of(tag("HT1")), surface, style).stream()
                .filter(segment -> segment.text().equals("HT1"))
                .findFirst()
                .orElseThrow();
        assertEquals(0xFFCF4A, tier.rgb());
    }

    @Test
    void customColourBeatsThePalette() {
        style.tierColors.put("HT1", 0x123456);
        assertEquals(0x123456, style.color(TierList.PVPHQ, Tier.parse("HT1")));
        assertEquals(Palette.DEFAULT.color(Tier.parse("HT2")), style.color(TierList.PVPHQ, Tier.parse("HT2")));
    }

    @Test
    void perListColourBeatsSharedOnlyWhenSwitchedOn() {
        style.tierColors.put("HT1", 0x111111);
        style.listColors.put(TierList.MCPVP, new java.util.HashMap<>(java.util.Map.of("HT1", 0x222222)));
        assertEquals(0x111111, style.color(TierList.MCPVP, Tier.parse("HT1")));

        style.perListColors = true;
        assertEquals(0x222222, style.color(TierList.MCPVP, Tier.parse("HT1")));
        assertEquals(0x111111, style.color(TierList.PVPHQ, Tier.parse("HT1")));
    }

    @Test
    void retiredIsDimmedByDefault() {
        int active = TagFormatter.format(List.of(tag("HT1")), surface, style).get(2).rgb();
        int dimmed = TagFormatter.format(List.of(retired("HT1")), surface, style).get(2).rgb();
        assertNotEquals(active, dimmed);
    }

    @Test
    void retiredCanBeMarkedOrIgnored() {
        style.retiredMark = Style.RetiredMark.SUFFIX;
        assertEquals("[HT1R]", text(retired("HT1")));
        style.retiredMark = Style.RetiredMark.NONE;
        assertEquals("[HT1]", text(retired("HT1")));
    }

    @Test
    void aHigherPeakFollowsInBrackets() {
        assertEquals("[HT3 (HT2)]", text(new Tag(TierList.PVPHQ, "sword", Tier.parse("HT3"), Tier.parse("HT2"), false, "EU")));
    }

    @Test
    void unrankedPlaceholder() {
        assertEquals("[-]", text(Tag.UNRANKED));
        style.longNames = true;
        assertEquals("[Unranked]", text(Tag.UNRANKED));
    }

    @Test
    void noTagsMeansNoSegments() {
        assertTrue(TagFormatter.format(List.of(), surface, style).isEmpty());
    }

    @Test
    void everyPaletteColoursEveryTier() {
        for (Palette palette : Palette.values()) {
            for (dev.tierprofiles.core.model.Band band : dev.tierprofiles.core.model.Band.values()) {
                for (int level = Tier.MIN_LEVEL; level <= Tier.MAX_LEVEL; level++) {
                    int color = palette.color(Tier.of(band, level));
                    assertTrue(color >= 0 && color <= 0xFFFFFF);
                }
            }
        }
    }

    @Test
    void defaultPaletteMatchesTheSpec() {
        assertEquals(0xFFCF4A, Palette.DEFAULT.color(Tier.parse("HT1")));
        assertEquals(0xE6B422, Palette.DEFAULT.color(Tier.parse("LT1")));
        assertEquals(0x6A6F78, Palette.DEFAULT.color(Tier.parse("LT5")));
    }

    @Test
    void midTierSitsBetweenHighAndLow() {
        assertEquals(Colors.mix(0xFFCF4A, 0xE6B422, 0.5f), Palette.DEFAULT.color(Tier.parse("MT1")));
    }

    @Test
    void hexHelpers() {
        assertEquals(0xFF7777, Colors.parseHex("#FF7777"));
        assertEquals(0xFF7777, Colors.parseHex("ff7777"));
        assertEquals(null, Colors.parseHex("#FFF"));
        assertEquals(null, Colors.parseHex("banana"));
        assertEquals("#FF7777", Colors.toHex(0xFF7777));
    }
}
