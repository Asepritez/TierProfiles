package dev.tierprofiles.core;

import static dev.tierprofiles.core.Fixtures.entry;
import static dev.tierprofiles.core.Fixtures.ok;
import static dev.tierprofiles.core.Fixtures.player;
import static dev.tierprofiles.core.Fixtures.settings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tierprofiles.core.anim.CardPose;
import dev.tierprofiles.core.anim.Easing;
import dev.tierprofiles.core.anim.Reveal;
import dev.tierprofiles.core.anim.Smoothed;
import dev.tierprofiles.core.anim.Stagger;
import dev.tierprofiles.core.logic.CardSummary;
import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierEntry;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.model.TimeAgo;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.core.settings.CardSettings.AnimationStyle;
import dev.tierprofiles.core.settings.Settings;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class AnimationTest {
    private static final float CLOSE = 0.001f;

    @ParameterizedTest
    @EnumSource(Easing.class)
    void everyCurveStartsAndEndsInTheRightPlace(Easing easing) {
        assertEquals(0, easing.apply(0), CLOSE);
        assertEquals(1, easing.apply(1), CLOSE);
    }

    @Test
    void outCubicIsFastFirst() {
        assertTrue(Easing.OUT_CUBIC.apply(0.5f) > 0.8f);
    }

    @Test
    void outBackOvershootsForAPop() {
        assertTrue(Easing.OUT_BACK.apply(0.8f) > 1);
    }

    @Test
    void revealOpensOverItsDuration() {
        Reveal reveal = new Reveal();
        reveal.set(true, 1000, 200);
        assertEquals(0, reveal.position(1000), CLOSE);
        assertEquals(0.5f, reveal.position(1100), CLOSE);
        assertEquals(1, reveal.position(1200), CLOSE);
        assertEquals(1, reveal.position(9000), CLOSE);
    }

    @Test
    void revealPositionDoesntDependOnHowOftenItIsRead() {
        Reveal often = new Reveal();
        Reveal rarely = new Reveal();
        often.set(true, 0, 300);
        rarely.set(true, 0, 300);
        for (long now = 0; now <= 150; now += 4) {
            often.position(now);
        }
        assertEquals(rarely.position(150), often.position(150), CLOSE);
    }

    @Test
    void reversingPartWayDoesntJump() {
        Reveal reveal = new Reveal();
        reveal.set(true, 0, 200);
        float before = reveal.position(100);
        reveal.set(false, 100, 200);
        assertEquals(before, reveal.position(100), CLOSE);
        assertTrue(reveal.position(150) < before);
    }

    @Test
    void aClosingCardStaysVisibleUntilItHasGone() {
        Reveal reveal = new Reveal();
        reveal.set(true, 0, 100);
        reveal.set(false, 200, 100);
        assertFalse(reveal.isOpen());
        assertTrue(reveal.isVisible(250));
        assertFalse(reveal.isVisible(300));
    }

    @Test
    void zeroDurationIsInstant() {
        Reveal reveal = new Reveal();
        reveal.set(true, 0, 0);
        assertEquals(1, reveal.position(0), CLOSE);
        reveal.set(false, 5, 0);
        assertFalse(reveal.isVisible(5));
    }

    @Test
    void staggerWaitsThenPlays() {
        assertEquals(0, Stagger.progress(50, 100, 200), CLOSE);
        assertEquals(0, Stagger.progress(100, 100, 200), CLOSE);
        assertEquals(1, Stagger.progress(300, 100, 200), CLOSE);
        assertEquals(1, Stagger.progress(5000, 100, 200), CLOSE);
    }

    @Test
    void laterItemsLagBehind() {
        assertTrue(Stagger.progress(150, 40, 200) > Stagger.progress(150, 80, 200));
    }

    @Test
    void zeroDurationStaggerIsInstant() {
        assertEquals(1, Stagger.progress(0, 0, 0), CLOSE);
    }

    private static float runAt(int framesPerSecond, long totalMillis) {
        Smoothed smoothed = new Smoothed(40);
        smoothed.snap(0, 0);
        float value = 0;
        double frame = 1000.0 / framesPerSecond;
        for (double now = frame; now <= totalMillis; now += frame) {
            value = smoothed.update(100, Math.round(now));
        }
        return smoothed.update(100, totalMillis);
    }

    @Test
    void smoothingArrivesTogetherAtAnyFrameRate() {
        float at30 = runAt(30, 120);
        float at60 = runAt(60, 120);
        float at240 = runAt(240, 120);
        assertEquals(at60, at240, 1.5f);
        assertEquals(at60, at30, 1.5f);
    }

    @Test
    void halfLifeClosesHalfTheGap() {
        Smoothed smoothed = new Smoothed(100);
        smoothed.snap(0, 0);
        assertEquals(50, smoothed.update(100, 100), 0.5f);
    }

    @Test
    void theFirstUpdateStartsInPlaceSoItDoesntFlyIn() {
        Smoothed smoothed = new Smoothed(100);
        assertEquals(42, smoothed.update(42, 1000), CLOSE);
    }

    @Test
    void aStalledFrameJustLandsOnTarget() {
        Smoothed smoothed = new Smoothed(40);
        smoothed.snap(0, 0);
        assertEquals(100, smoothed.update(100, 60_000), CLOSE);
    }

    @Test
    void glideStartsOffToTheSideAndLands() {
        CardPose start = CardPose.of(AnimationStyle.GLIDE, 0, 200);
        assertEquals(200, start.offsetX(), CLOSE);
        assertEquals(0, start.alpha(), CLOSE);
        assertEquals(CardPose.RESTING, CardPose.of(AnimationStyle.GLIDE, 1, 200));
    }

    @Test
    void glideDirectionFollowsTheSign() {
        assertTrue(CardPose.of(AnimationStyle.GLIDE, 0.5f, -200).offsetX() < 0);
    }

    @Test
    void fadeOnlyChangesOpacity() {
        CardPose pose = CardPose.of(AnimationStyle.FADE, 0.5f, 200);
        assertEquals(new CardPose(0, 1, 0.5f), pose);
    }

    @Test
    void scaleGrowsIntoPlace() {
        assertTrue(CardPose.of(AnimationStyle.SCALE, 0, 200).scale() < 1);
        assertEquals(CardPose.RESTING, CardPose.of(AnimationStyle.SCALE, 1, 200));
    }

    @Test
    void offIsAlwaysAtRest() {
        assertEquals(CardPose.RESTING, CardPose.of(AnimationStyle.OFF, 0, 200));
    }

    @Test
    void reduceMotionTurnsAnimationOffWhateverTheStyle() {
        CardSettings card = new CardSettings();
        card.animationStyle = AnimationStyle.GLIDE;
        assertEquals(AnimationStyle.OFF, card.effectiveStyle(true));
        assertEquals(AnimationStyle.GLIDE, card.effectiveStyle(false));
    }

    @Test
    void speedScalesDurations() {
        CardSettings card = new CardSettings();
        card.animationSpeed = 2;
        assertEquals(100, card.scaled(200));
    }

    @Test
    void speedIsClamped() {
        Settings settings = new Settings();
        settings.card.animationSpeed = 900;
        settings.sanitize();
        assertEquals(CardSettings.MAX_SPEED, settings.card.animationSpeed, CLOSE);

        settings.card.animationSpeed = Float.NaN;
        settings.sanitize();
        assertEquals(1, settings.card.animationSpeed, CLOSE);
    }

    @ParameterizedTest
    @CsvSource({"30, now", "120, 2m", "7200, 2h", "172800, 2d", "5184000, 2mo", "63072000, 2y"})
    void compactDates(long elapsed, String expected) {
        assertEquals(expected, TimeAgo.compact(1000, 1000 + elapsed));
    }

    private static ListProfile standing(int position, double points) {
        return new ListProfile(ListStatus.OK, null, "EU", null, points, position, List.of(entry("sword", "HT3")));
    }

    @Test
    void headlineIsTheBestPositionAndTheSumOfAllPoints() {
        PlayerProfile profile = player(
                TierList.MCTIERS, standing(56, 102),
                TierList.MCPVP, standing(1, 280),
                TierList.PVPTIERS, standing(175, 64),
                TierList.SUBTIERS, standing(160, 83));
        CardSummary summary = CardSummary.of(profile, settings().filters);

        assertEquals(1, summary.bestPosition());
        assertEquals(TierList.MCPVP, summary.bestList());
        assertEquals(529, summary.totalPoints());
    }

    @Test
    void aTieGoesToTheListYouTrustMost() {
        PlayerProfile profile = player(TierList.MCPVP, standing(3, 10), TierList.MCTIERS, standing(3, 10));
        assertEquals(TierList.MCTIERS, CardSummary.of(profile, settings().filters).bestList());
    }

    @Test
    void disabledListsDontCount() {
        Settings settings = settings();
        settings.filters.disabledLists.add(TierList.MCPVP);
        PlayerProfile profile = player(TierList.MCTIERS, standing(9, 50), TierList.MCPVP, standing(1, 280));
        CardSummary summary = CardSummary.of(profile, settings.filters);

        assertEquals(9, summary.bestPosition());
        assertEquals(50, summary.totalPoints());
    }

    @Test
    void aPlayerWithNoStandingsHasNoPosition() {
        CardSummary summary = CardSummary.of(player(TierList.MCTIERS, ListProfile.UNRANKED), settings().filters);
        assertNull(summary.bestPosition());
        assertEquals(0, summary.totalPoints());
    }

    @Test
    void rowDateIsTheNewestActiveTier() {
        ListProfile result = ok(
                new TierEntry("sword", Tier.parse("HT3"), null, 100L, false, null, null),
                new TierEntry("axe", Tier.parse("LT3"), null, 300L, false, null, null),
                new TierEntry("mace", Tier.parse("HT2"), null, 900L, true, null, null));
        assertEquals(300L, CardSummary.latestDate(result));
    }

    @Test
    void aListWithNoDatesHasNoRowDate() {
        assertNull(CardSummary.latestDate(ok(entry("sword", "HT3"))));
    }
}
