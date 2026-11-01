package dev.tierprofiles.core;

import static dev.tierprofiles.core.Fixtures.entry;
import static dev.tierprofiles.core.Fixtures.ok;
import static dev.tierprofiles.core.Fixtures.player;
import static dev.tierprofiles.core.Fixtures.settings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tierprofiles.core.logic.Tag;
import dev.tierprofiles.core.logic.TagSelector;
import dev.tierprofiles.core.logic.TagSelector.Context;
import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Filters.KitMode;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Surface;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TagSelectorTest {
    private static List<Tag> select(PlayerProfile profile, Settings settings) {
        return TagSelector.select(profile, Surface.NAMETAG, settings, Context.NONE);
    }

    private static List<String> labels(List<Tag> tags) {
        List<String> out = new ArrayList<>();
        for (Tag tag : tags) {
            out.add(tag.isUnranked() ? "unranked" : tag.list().id() + ":" + tag.kit() + ":" + tag.tier().label());
        }
        return out;
    }

    private static final PlayerProfile HT4_ONLY = player(TierList.MCTIERS, ok(entry("sword", "HT4")));

    @Test
    void picksTheStrongestKitOnAList() {
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("axe", "LT4"), entry("sword", "HT2"), entry("mace", "HT5")));
        assertEquals(List.of("mctiers:sword:HT2"), labels(select(profile, settings())));
    }

    @Test
    void onlyLt3AndAboveHidesHt4() {
        Settings settings = settings();
        settings.surface(Surface.NAMETAG).minimumTier = "LT3";
        assertEquals(List.of(), select(HT4_ONLY, settings));
    }

    @Test
    void onlyLt3AndAboveKeepsLt3AndHt3() {
        Settings settings = settings();
        settings.surface(Surface.NAMETAG).minimumTier = "LT3";
        assertEquals(1, select(player(TierList.MCTIERS, ok(entry("sword", "LT3"))), settings).size());
        assertEquals(1, select(player(TierList.MCTIERS, ok(entry("sword", "HT3"))), settings).size());
    }

    @Test
    void minimumAppliesToMcpvpTiersToo() {
        Settings settings = settings();
        settings.surface(Surface.NAMETAG).minimumTier = "LT3";
        settings.filters.listPriority = new ArrayList<>(List.of(TierList.MCPVP));
        assertEquals(1, select(player(TierList.MCPVP, ok(entry("sword", "MT3"))), settings).size());
        assertEquals(0, select(player(TierList.MCPVP, ok(entry("sword", "ST3"))), settings).size());
        assertEquals(0, select(player(TierList.MCPVP, ok(entry("sword", "HT6"))), settings).size());
    }

    @Test
    void aFailedFilterFallsBackToTheNextStrongestKitNotNothing() {
        Settings settings = settings();
        settings.filters.hideRetired = true;
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("sword", "HT1", null, true), entry("axe", "LT2")));
        assertEquals(List.of("mctiers:axe:LT2"), labels(select(profile, settings)));
    }

    @Test
    void minimumIsPerSurface() {
        Settings settings = settings();
        settings.surface(Surface.NAMETAG).minimumTier = "LT3";
        assertEquals(0, TagSelector.select(HT4_ONLY, Surface.NAMETAG, settings, Context.NONE).size());
        assertEquals(1, TagSelector.select(HT4_ONLY, Surface.TAB, settings, Context.NONE).size());
    }

    @Test
    void perListMinimumOnlyAffectsThatList() {
        Settings settings = settings();
        settings.filters.listMinimums.put(TierList.MCPVP, "HT2");
        settings.surface(Surface.NAMETAG).maxTags = 5;
        PlayerProfile profile = player(
                TierList.MCTIERS, ok(entry("sword", "HT4")),
                TierList.MCPVP, ok(entry("sword", "HT4")));
        assertEquals(List.of("mctiers:sword:HT4"), labels(select(profile, settings)));
    }

    @Test
    void surfaceAndListMinimumsMustBothPass() {
        Settings settings = settings();
        settings.surface(Surface.NAMETAG).minimumTier = "HT4";
        settings.filters.listMinimums.put(TierList.MCTIERS, "HT3");
        assertEquals(0, select(HT4_ONLY, settings).size());
    }

    @Test
    void disabledSurfaceShowsNothing() {
        Settings settings = settings();
        settings.surface(Surface.NAMETAG).enabled = false;
        assertEquals(0, select(HT4_ONLY, settings).size());
    }

    @Test
    void chatIsOffByDefault() {
        assertFalse(settings().surface(Surface.CHAT).enabled);
        assertEquals(0, TagSelector.select(HT4_ONLY, Surface.CHAT, settings(), Context.NONE).size());
    }

    @Test
    void retiredTiersShowUnlessHidden() {
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("sword", "HT2", null, true)));
        Tag tag = select(profile, settings()).getFirst();
        assertTrue(tag.retired());

        Settings hiding = settings();
        hiding.filters.hideRetired = true;
        assertEquals(0, select(profile, hiding).size());
    }

    @Test
    void peakModeShowsThePeak() {
        Settings settings = settings();
        settings.filters.showPeak = true;
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("sword", "HT3", "HT2", false)));
        assertEquals(List.of("mctiers:sword:HT2"), labels(select(profile, settings)));
    }

    @Test
    void peakModeFallsBackToCurrentWhenThereIsNoPeak() {
        Settings settings = settings();
        settings.filters.showPeak = true;
        assertEquals(List.of("mctiers:sword:HT4"), labels(select(HT4_ONLY, settings)));
    }

    @Test
    void peakModePicksTheBestPeakNotTheBestCurrent() {
        Settings settings = settings();
        settings.filters.showPeak = true;
        PlayerProfile profile = player(TierList.MCTIERS, ok(
                entry("sword", "HT3", "HT3", false),
                entry("axe", "LT4", "HT1", false)));
        assertEquals(List.of("mctiers:axe:HT1"), labels(select(profile, settings)));
    }

    @Test
    void minimumIsCheckedAgainstWhatIsShown() {
        Settings settings = settings();
        settings.filters.showPeak = true;
        settings.surface(Surface.NAMETAG).minimumTier = "HT2";
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("sword", "LT4", "HT2", false)));
        assertEquals(1, select(profile, settings).size());
    }

    @Test
    void mctiersBeatsMcpvpByDefault() {
        PlayerProfile profile = player(
                TierList.MCTIERS, ok(entry("sword", "HT4")),
                TierList.MCPVP, ok(entry("sword", "HT1")));
        assertEquals(List.of("mctiers:sword:HT4"), labels(select(profile, settings())));
    }

    @Test
    void mcpvpIsNeverTopPriorityByDefault() {
        assertEquals(TierList.MCTIERS, settings().filters.listPriority.getFirst());
        assertTrue(settings().filters.listPriority.indexOf(TierList.MCPVP) > 0);
    }

    @Test
    void listPriorityCanBeChanged() {
        Settings settings = settings();
        settings.filters.listPriority = new ArrayList<>(List.of(TierList.MCPVP, TierList.MCTIERS));
        PlayerProfile profile = player(
                TierList.MCTIERS, ok(entry("sword", "HT1")),
                TierList.MCPVP, ok(entry("sword", "HT4")));
        assertEquals(List.of("mcpvp:sword:HT4"), labels(select(profile, settings)));
    }

    @Test
    void theNextListGetsTheSlotWhenTheFirstOffersNothing() {
        PlayerProfile profile = player(
                TierList.MCTIERS, ListProfile.UNRANKED,
                TierList.PVPTIERS, ListProfile.unavailable("timeout"),
                TierList.SUBTIERS, ok(entry("minecart", "LT3")));
        assertEquals(List.of("subtiers:minecart:LT3"), labels(select(profile, settings())));
    }

    @Test
    void moreRoomMeansMoreLists() {
        Settings settings = settings();
        settings.surface(Surface.NAMETAG).maxTags = 2;
        PlayerProfile profile = player(
                TierList.MCTIERS, ok(entry("sword", "HT4")),
                TierList.PVPTIERS, ok(entry("axe", "LT3")),
                TierList.SUBTIERS, ok(entry("bed", "HT1")));
        assertEquals(List.of("mctiers:sword:HT4", "pvptiers:axe:LT3"), labels(select(profile, settings)));
    }

    @Test
    void disabledListsAreSkipped() {
        Settings settings = settings();
        settings.filters.disabledLists.add(TierList.MCTIERS);
        PlayerProfile profile = player(
                TierList.MCTIERS, ok(entry("sword", "HT1")),
                TierList.PVPTIERS, ok(entry("sword", "HT4")));
        assertEquals(List.of("pvptiers:sword:HT4"), labels(select(profile, settings)));
    }

    @Test
    void selectedKitsOnlyCountThoseKits() {
        Settings settings = settings();
        settings.filters.kitMode = KitMode.SELECTED;
        settings.filters.selectedKits = new ArrayList<>(List.of("axe"));
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("sword", "HT1"), entry("axe", "LT4")));
        assertEquals(List.of("mctiers:axe:LT4"), labels(select(profile, settings)));
    }

    @Test
    void selectedKitOrderBreaksTies() {
        Settings settings = settings();
        settings.filters.kitMode = KitMode.SELECTED;
        settings.filters.selectedKits = new ArrayList<>(List.of("axe", "sword"));
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("sword", "HT2"), entry("axe", "HT2")));
        assertEquals(List.of("mctiers:axe:HT2"), labels(select(profile, settings)));
    }

    @Test
    void anActiveTierBeatsAnEqualRetiredOne() {
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("sword", "HT2", null, true), entry("axe", "HT2", null, false)));
        assertEquals(List.of("mctiers:axe:HT2"), labels(select(profile, settings())));
    }

    @Test
    void autoModeUsesTheDetectedKit() {
        Settings settings = settings();
        settings.filters.kitMode = KitMode.AUTO;
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("sword", "HT1"), entry("mace", "LT4")));
        List<Tag> tags = TagSelector.select(profile, Surface.NAMETAG, settings, new Context(false, "mace"));
        assertEquals(List.of("mctiers:mace:LT4"), labels(tags));
    }

    @Test
    void autoModeWithNoGuessActsLikeHighest() {
        Settings settings = settings();
        settings.filters.kitMode = KitMode.AUTO;
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("sword", "HT1"), entry("mace", "LT4")));
        assertEquals(List.of("mctiers:sword:HT1"), labels(select(profile, settings)));
    }

    @Test
    void ownTagCanBeHidden() {
        Settings settings = settings();
        settings.filters.hideOwnTag = true;
        assertEquals(0, TagSelector.select(HT4_ONLY, Surface.TAB, settings, new Context(true, null)).size());
        assertEquals(1, TagSelector.select(HT4_ONLY, Surface.TAB, settings, new Context(false, null)).size());
    }

    @Test
    void unrankedShowsOnlyWhenAsked() {
        Settings settings = settings();
        PlayerProfile profile = player(
                TierList.MCTIERS, ListProfile.UNRANKED, TierList.MCPVP, ListProfile.UNRANKED,
                TierList.PVPTIERS, ListProfile.UNRANKED, TierList.SUBTIERS, ListProfile.UNRANKED,
                TierList.PVPHQ, ListProfile.UNRANKED);
        assertEquals(0, select(profile, settings).size());

        settings.filters.hideUnranked = false;
        assertEquals(List.of("unranked"), labels(select(profile, settings)));
    }

    @Test
    void aListBeingDownMeansWeDontClaimUnranked() {
        Settings settings = settings();
        settings.filters.hideUnranked = false;
        PlayerProfile profile = player(TierList.MCTIERS, ListProfile.UNRANKED, TierList.PVPHQ, ListProfile.unavailable("timeout"));
        assertEquals(0, select(profile, settings).size());
    }

    @Test
    void aFilteredOutRankedPlayerIsNotCalledUnranked() {
        Settings settings = settings();
        settings.filters.hideUnranked = false;
        settings.surface(Surface.NAMETAG).minimumTier = "LT3";
        assertEquals(0, select(HT4_ONLY, settings).size());
    }

    @Test
    void anUnknownPlayerGivesNoTags() {
        assertEquals(0, select(null, settings()).size());
    }

    @Test
    void tagCarriesTheRegionOfItsList() {
        assertEquals("EU", select(HT4_ONLY, settings()).getFirst().region());
    }
}
