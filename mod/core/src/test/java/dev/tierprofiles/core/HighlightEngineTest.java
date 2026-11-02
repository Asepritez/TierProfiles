package dev.tierprofiles.core;

import static dev.tierprofiles.core.Fixtures.entry;
import static dev.tierprofiles.core.Fixtures.ok;
import static dev.tierprofiles.core.Fixtures.player;
import static dev.tierprofiles.core.Fixtures.settings;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tierprofiles.core.logic.Gradient;
import dev.tierprofiles.core.logic.HighlightEngine;
import dev.tierprofiles.core.logic.NameStyle;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.HighlightRule;
import dev.tierprofiles.core.settings.Settings;
import java.util.List;
import org.junit.jupiter.api.Test;

class HighlightEngineTest {
    private static final int RED = 0xFF0000;
    private static final int PURPLE = 0xAA00FF;

    private static HighlightRule purpleBoldHt1() {
        HighlightRule rule = HighlightRule.of(TierList.MCTIERS, "HT1", PURPLE);
        rule.bold = true;
        return rule;
    }

    private static HighlightRule redHt3() {
        return HighlightRule.of(TierList.MCTIERS, "HT3", RED);
    }

    private static PlayerProfile mctiers(String rank) {
        return player(TierList.MCTIERS, ok(entry("sword", rank)));
    }

    private static NameStyle run(PlayerProfile profile, Settings settings, HighlightRule... rules) {
        return HighlightEngine.evaluate(profile, List.of(rules), settings.filters);
    }

    @Test
    void ht3AboveTurnsTheNameRed() {
        assertEquals(RED, run(mctiers("HT3"), settings(), redHt3()).color());
        assertEquals(RED, run(mctiers("LT2"), settings(), redHt3()).color());
        assertEquals(RED, run(mctiers("HT1"), settings(), redHt3()).color());
    }

    @Test
    void belowHt3LeavesTheNameAlone() {
        assertTrue(run(mctiers("LT3"), settings(), redHt3()).isPlain());
        assertTrue(run(mctiers("HT4"), settings(), redHt3()).isPlain());
    }

    @Test
    void firstMatchWinsSoOrderMatters() {
        NameStyle top = run(mctiers("HT1"), settings(), purpleBoldHt1(), redHt3());
        assertEquals(PURPLE, top.color());
        assertTrue(top.bold());

        NameStyle lower = run(mctiers("HT2"), settings(), purpleBoldHt1(), redHt3());
        assertEquals(RED, lower.color());
        assertFalse(lower.bold());
    }

    @Test
    void puttingTheBroadRuleFirstShadowsTheNarrowOne() {
        assertEquals(RED, run(mctiers("HT1"), settings(), redHt3(), purpleBoldHt1()).color());
    }

    @Test
    void disabledRulesAreSkipped() {
        HighlightRule off = redHt3();
        off.enabled = false;
        assertTrue(run(mctiers("HT1"), settings(), off).isPlain());
    }

    @Test
    void aRuleOnOneListIgnoresOtherLists() {
        PlayerProfile profile = player(TierList.MCPVP, ok(entry("sword", "HT1")));
        assertTrue(run(profile, settings(), redHt3()).isPlain());
    }

    @Test
    void aRuleOnAnyListChecksThemAll() {
        PlayerProfile profile = player(TierList.SUBTIERS, ok(entry("bed", "HT1")));
        assertEquals(RED, run(profile, settings(), HighlightRule.of(null, "HT3", RED)).color());
    }

    @Test
    void aRuleCanRequireAKit() {
        HighlightRule rule = HighlightRule.of(null, "HT3", RED);
        rule.kit = "mace";
        assertTrue(run(player(TierList.MCTIERS, ok(entry("sword", "HT1"))), settings(), rule).isPlain());
        assertEquals(RED, run(player(TierList.MCTIERS, ok(entry("mace", "HT3"))), settings(), rule).color());
    }

    @Test
    void anyKitOnTheListCanMatch() {
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("axe", "LT5"), entry("sword", "HT2")));
        assertEquals(RED, run(profile, settings(), redHt3()).color());
    }

    @Test
    void retiredTiersDontCountWhenHidden() {
        HighlightRule rule = redHt3();
        rule.ignoreRetired = true;
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("sword", "HT1", null, true)));
        assertTrue(run(profile, settings(), rule).isPlain());
        assertEquals(RED, run(profile, settings(), redHt3()).color());
    }

    @Test
    void peakModeAppliesToRules() {
        HighlightRule rule = redHt3();
        rule.usePeak = true;
        PlayerProfile profile = player(TierList.MCTIERS, ok(entry("sword", "LT5", "HT1", false)));
        assertEquals(RED, run(profile, settings(), rule).color());
        assertTrue(run(profile, settings(), redHt3()).isPlain());
    }

    @Test
    void hiddenTagsCanStillTriggerRules() {
        Settings settings = settings();
        settings.surface(dev.tierprofiles.core.settings.Surface.NAMETAG).minimumTier = "HT1";
        assertEquals(RED, run(mctiers("HT3"), settings, redHt3()).color());
    }

    @Test
    void disabledListsDontTriggerRules() {
        Settings settings = settings();
        settings.filters.disabledLists.add(TierList.MCTIERS);
        assertTrue(run(mctiers("HT1"), settings, redHt3()).isPlain());
    }

    @Test
    void hiddenKitsDontTriggerRules() {
        Settings settings = settings();
        settings.filters.hiddenKits.add("sword");
        assertTrue(run(mctiers("HT1"), settings, redHt3()).isPlain());
    }

    @Test
    void everyEffectCarriesThrough() {
        HighlightRule rule = redHt3();
        rule.gradientEnd = PURPLE;
        rule.bold = true;
        rule.glow = true;
        rule.star = true;
        NameStyle style = run(mctiers("HT1"), settings(), rule);
        assertEquals(new NameStyle(RED, PURPLE, true, true, true), style);
        assertTrue(style.isGradient());
    }

    @Test
    void comparisonsMeanWhatTheySay() {
        HighlightRule at = redHt3();
        at.comparison = HighlightRule.Comparison.AT;
        assertEquals(RED, run(mctiers("HT3"), settings(), at).color());
        assertTrue(run(mctiers("HT2"), settings(), at).isPlain());

        HighlightRule above = redHt3();
        above.comparison = HighlightRule.Comparison.ABOVE;
        assertTrue(run(mctiers("HT3"), settings(), above).isPlain());
        assertEquals(RED, run(mctiers("HT2"), settings(), above).color());
    }

    @Test
    void anUnknownPlayerHasNoStyle() {
        assertEquals(NameStyle.NONE, HighlightEngine.evaluate(null, List.of(redHt3()), settings().filters));
    }

    @Test
    void gradientRunsEndToEnd() {
        assertArrayEquals(new int[] {0x000000, 0x808080, 0xFFFFFF}, Gradient.across(3, 0x000000, 0xFFFFFF));
        assertArrayEquals(new int[] {0x123456}, Gradient.across(1, 0x123456, 0xFFFFFF));
        assertArrayEquals(new int[0], Gradient.across(0, 0, 0));
    }
}
