package dev.tierprofiles.core;

import static dev.tierprofiles.core.Fixtures.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tierprofiles.core.logic.KitDetector;
import dev.tierprofiles.core.model.Kits;
import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.ProfileParser;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierEntry;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.model.TimeAgo;
import dev.tierprofiles.core.model.Title;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ModelTest {
    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static ListProfile withPoints(double points) {
        return new ListProfile(ListStatus.OK, null, null, null, points, null, List.of());
    }

    @ParameterizedTest
    @CsvSource({
        "400, GRANDMASTER", "9999, GRANDMASTER", "399.9, MASTER", "250, MASTER", "249, ACE", "100, ACE",
        "99, SPECIALIST", "50, SPECIALIST", "49, CADET", "20, CADET", "19, NOVICE", "10, NOVICE", "9, ROOKIE", "1, ROOKIE",
    })
    void titleLadder(double points, Title expected) {
        assertEquals(expected, Title.forPoints(points));
    }

    @Test
    void noTitleBelowOnePoint() {
        assertNull(Title.forPoints(0.99));
        assertNull(Title.forPoints(0));
    }

    @Test
    void mcpvpTitleIsLabelledAsOurs() {
        Title.Earned earned = Title.of(TierList.MCPVP, withPoints(280));
        assertEquals(Title.MASTER, earned.title());
        assertEquals("Combat Master (TierProfiles title)", earned.label());
    }

    @Test
    void pvpTiersAndSubTiersPointsUseTheSameLadder() {
        assertEquals(Title.MASTER, Title.of(TierList.PVPTIERS, withPoints(342)).title());
        assertEquals(Title.GRANDMASTER, Title.of(TierList.SUBTIERS, withPoints(500)).title());
    }

    @Test
    void pvphqHasNoTitle() {
        assertNull(Title.of(TierList.PVPHQ, withPoints(500)));
    }

    @Test
    void noPointsNoTitle() {
        assertNull(Title.of(TierList.MCPVP, ListProfile.UNRANKED));
    }

    @Test
    void mcpvpIsLabelledBeta() {
        assertTrue(TierList.MCPVP.isBeta());
        assertFalse(TierList.PVPTIERS.isBeta());
    }

    @ParameterizedTest
    @CsvSource({
        "0, just now", "59, just now", "60, 1 minute ago", "120, 2 minutes ago", "3600, 1 hour ago",
        "86400, 1 day ago", "172800, 2 days ago", "2592000, 1 month ago", "31536000, 1 year ago", "63072000, 2 years ago",
    })
    void timeAgo(long elapsed, String expected) {
        assertEquals(expected, TimeAgo.format(1000, 1000 + elapsed));
    }

    @Test
    void timeFromTheFutureDoesntGoNegative() {
        assertEquals("just now", TimeAgo.format(5000, 1000));
    }

    @Test
    void parsesARealServerProfile() {
        PlayerProfile profile = ProfileParser.parse(json("""
                {"uuid": "1246a0ec-662b-45de-bb0b-d68cb1cce800", "name": "JokingDK", "lists": {
                  "subtiers": {"status": "ok", "region": "EU", "country": null, "points": 102, "overall": 56, "tiers": [
                    {"kit": "smp", "rank": "HT2", "peak": "HT2", "attained": 1707879318, "retired": true},
                    {"kit": "sword", "rank": "LT3", "peak": null, "attained": null, "retired": false}]},
                  "mcpvp": {"status": "ok", "region": "EU", "country": "DK", "points": 280.5, "overall": 1, "tiers": [
                    {"kit": "spear", "rank": "MT3", "peak": "MT3", "attained": null, "retired": false}]},
                  "pvptiers": {"status": "unranked"},
                  "pvphq": {"status": "unavailable", "reason": "timeout"}
                }}"""));

        ListProfile rankings = profile.list(TierList.SUBTIERS);
        assertEquals(ListStatus.OK, rankings.status());
        assertEquals(102.0, rankings.points());
        assertEquals(56, rankings.overall());

        TierEntry smp = rankings.tiers().getFirst();
        assertEquals("smp", smp.kit());
        assertEquals(Tier.parse("HT2"), smp.rank());
        assertEquals(1707879318L, smp.attained());
        assertTrue(smp.retired());
        assertNull(rankings.tiers().get(1).peak());

        assertEquals(280.5, profile.list(TierList.MCPVP).points());
        assertEquals("DK", profile.list(TierList.MCPVP).country());
        assertEquals(ListStatus.UNRANKED, profile.list(TierList.PVPTIERS).status());
        assertEquals("timeout", profile.list(TierList.PVPHQ).reason());
    }

    @Test
    void anOldReplyNamingMctiersStillReadsTheOtherLists() {
        PlayerProfile profile = ProfileParser.parse(json("""
                {"uuid": "1246a0ec-662b-45de-bb0b-d68cb1cce800", "lists": {
                  "mctiers": {"status": "ok", "tiers": [{"kit": "sword", "rank": "HT1"}]},
                  "mcpvp": {"status": "unranked"}}}"""));
        assertEquals(ListStatus.UNRANKED, profile.list(TierList.MCPVP).status());
        assertEquals(ListStatus.UNAVAILABLE, profile.list(TierList.SUBTIERS).status());
    }

    @Test
    void aListTheServerLeftOutIsUnavailableNotUnranked() {
        PlayerProfile profile = ProfileParser.parse(json("""
                {"uuid": "1246a0ec-662b-45de-bb0b-d68cb1cce800", "lists": {}}"""));
        assertEquals(ListStatus.UNAVAILABLE, profile.list(TierList.SUBTIERS).status());
    }

    @Test
    void badTiersAreSkippedNotFatal() {
        PlayerProfile profile = ProfileParser.parse(json("""
                {"uuid": "1246a0ec-662b-45de-bb0b-d68cb1cce800", "lists": {"subtiers": {"status": "ok", "tiers": [
                  {"kit": "sword", "rank": "ZZ9"}, {"rank": "HT1"}, "junk", {"kit": "axe", "rank": "LT2"}]}}}"""));
        assertEquals(List.of("axe"), profile.list(TierList.SUBTIERS).tiers().stream().map(TierEntry::kit).toList());
    }

    @Test
    void noUuidMeansNoProfile() {
        assertNull(ProfileParser.parse(json("{\"name\": \"x\"}")));
        assertNull(ProfileParser.parse(json("{\"uuid\": \"banana\"}")));
    }

    @Test
    void regionComesFromTheFirstListThatKnows() {
        PlayerProfile profile = Fixtures.player(TierList.SUBTIERS, ok());
        assertEquals("EU", profile.region(TierList.DEFAULT_PRIORITY));
    }

    @Test
    void kitNames() {
        assertEquals("Neth Pot", Kits.displayName("neth_pot"));
        assertEquals("Brand New Kit", Kits.displayName("brand_new_kit"));
        assertEquals("", Kits.displayName("__"));
    }

    @Test
    void kitDetectorGuessesFromInventory() {
        assertEquals("mace", KitDetector.detect(Set.of("minecraft:mace", "minecraft:diamond_sword")));
        assertEquals("crystal", KitDetector.detect(Set.of("minecraft:end_crystal", "minecraft:netherite_sword")));
        assertEquals("pot", KitDetector.detect(Set.of("minecraft:splash_potion")));
        assertEquals("neth_pot", KitDetector.detect(Set.of("minecraft:splash_potion", "minecraft:netherite_chestplate")));
        assertEquals("uhc", KitDetector.detect(Set.of("minecraft:lava_bucket", "minecraft:golden_apple")));
        assertEquals("sword", KitDetector.detect(Set.of("minecraft:diamond_sword")));
        assertNull(KitDetector.detect(Set.of("minecraft:dirt")));
        assertNull(KitDetector.detect(Set.of()));
    }
}
