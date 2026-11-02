package dev.tierprofiles.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.ProfileParser;
import dev.tierprofiles.core.model.TierEntry;
import dev.tierprofiles.core.model.TierList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Real replies saved from each list, the same ones the server's tests use. */
class DirectListsTest {
    private static final UUID JOKING = UUID.fromString("1246a0ec-662b-45de-bb0b-d68cb1cce800");
    private static final Path FIXTURES = Path.of("..", "..", "server", "test", "fixtures");

    private static String fixture(String name) {
        try {
            return Files.readString(FIXTURES.resolve(name));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Plays each list site. Anything not set up here is a 404, as for an unknown player. */
    private static final class FakeSites implements Transport {
        final Map<String, String> pages = new LinkedHashMap<>();
        final List<String> asked = new ArrayList<>();
        String downHost;
        /** Answers this host's next call with a 503, as PVPHQ does now and then. */
        String hiccupHost;

        @Override
        public synchronized String send(String method, String url, String body, String bearer) throws IOException {
            asked.add(url);
            if (downHost != null && url.contains(downHost)) {
                throw new IOException("connection refused");
            }
            if (hiccupHost != null && url.contains(hiccupHost)) {
                hiccupHost = null;
                throw new StatusException(503);
            }
            String page = pages.get(url);
            if (page == null) {
                throw new StatusException(404);
            }
            return page;
        }
    }

    private final long[] clock = {1_000_000};
    private final FakeSites sites = new FakeSites();
    private final DirectLists direct = new DirectLists(sites, () -> clock[0]);

    @AfterEach
    void stop() {
        direct.stop();
    }

    private PlayerProfile lookUp(UUID uuid, String name) {
        JsonObject reply = JsonParser.parseString(direct.lookup(Map.of(uuid, name))).getAsJsonObject();
        return ProfileParser.parse(reply.getAsJsonArray("profiles").get(0).getAsJsonObject());
    }

    private static TierEntry kit(ListProfile list, String kit) {
        return list.tiers().stream().filter(entry -> entry.kit().equals(kit)).findFirst().orElse(null);
    }

    @Test
    void readsEveryListStraightFromItsSite() {
        sites.pages.put(DirectLists.url(TierList.PVPTIERS, JOKING, "JokingDK"), fixture("mctiers-ranked.json"));
        sites.pages.put(DirectLists.url(TierList.MCPVP, JOKING, "JokingDK"), fixture("mcpvp-full.json"));
        sites.pages.put(DirectLists.url(TierList.PVPHQ, JOKING, "JokingDK"), fixture("pvphq-ranked.json"));
        sites.downHost = "subtiers.net";

        PlayerProfile profile = lookUp(JOKING, "JokingDK");

        ListProfile rankings = profile.list(TierList.PVPTIERS);
        assertEquals("NA", rankings.region());
        assertEquals(330, rankings.points());
        assertEquals("HT3", kit(rankings, "sword").rank().label());
        assertEquals("HT2", kit(rankings, "sword").peak().label());
        assertEquals(1772946009L, kit(rankings, "sword").attained());
        assertTrue(kit(rankings, "axe").retired());
        assertEquals(1, rankings.tiers().stream().filter(entry -> entry.kit().equals("neth_pot")).count(), "nethop and neth_pot are one kit");
        assertNull(kit(rankings, "nethop"));

        ListProfile mcpvp = profile.list(TierList.MCPVP);
        assertEquals("EU", mcpvp.region());
        assertEquals("MT3", kit(mcpvp, "spear").rank().label());
        assertEquals("LT1", kit(mcpvp, "minecart").rank().label());

        assertEquals(ListStatus.UNAVAILABLE, profile.list(TierList.SUBTIERS).status());
        assertEquals("LT5", kit(profile.list(TierList.PVPHQ), "spear").rank().label());
        assertNull(profile.list(TierList.PVPHQ).overall(), "99999 means no position");
    }

    @Test
    void mcPvpMatchesByUuidNotName() {
        UUID itz = UUID.fromString("06ec3577-3299-45fa-bbdf-613b1f86c8ab");
        sites.pages.put(DirectLists.url(TierList.MCPVP, itz, "ItzRealMe"), fixture("mcpvp-search-miss.json"));
        assertEquals(ListStatus.UNRANKED, lookUp(itz, "ItzRealMe").list(TierList.MCPVP).status());
    }

    @Test
    void pvpTiersGetsAnUndashedUuid() {
        assertEquals("https://pvptiers.com/api/profile/1246a0ec662b45debb0bd68cb1cce800", DirectLists.url(TierList.PVPTIERS, JOKING, "x"));
    }

    @Test
    void aListThatKeepsFailingIsLeftAloneForAMinute() {
        sites.downHost = "subtiers.net";
        for (int i = 0; i < DirectLists.STRIKES_TO_REST; i++) {
            assertEquals("network", lookUp(JOKING, "JokingDK").list(TierList.SUBTIERS).reason());
        }
        int before = sites.asked.size();

        assertEquals("circuit_open", lookUp(JOKING, "JokingDK").list(TierList.SUBTIERS).reason());
        assertEquals(before + TierList.values().length - 1, sites.asked.size(), "SubTiers wasn't asked again");

        clock[0] += DirectLists.COOLDOWN_MILLIS;
        sites.downHost = null;
        lookUp(JOKING, "JokingDK");
        assertTrue(sites.asked.stream().filter(url -> url.contains("subtiers.net")).count() >= 2);
    }

    @Test
    void aOneOffHiccupIsTriedAgainRatherThanCountedAsDown() {
        sites.pages.put(DirectLists.url(TierList.SUBTIERS, JOKING, "JokingDK"), fixture("mctiers-ranked.json"));
        sites.hiccupHost = "subtiers.net";
        assertEquals(ListStatus.OK, lookUp(JOKING, "JokingDK").list(TierList.SUBTIERS).status());
        assertEquals(2, sites.asked.stream().filter(url -> url.contains("subtiers.net")).count());
    }

    @Test
    void namesTooShortForMcPvpAreNotSearched() {
        assertNull(DirectLists.url(TierList.MCPVP, JOKING, "ab"));
        assertEquals("name_too_short", lookUp(JOKING, "ab").list(TierList.MCPVP).reason());
    }

    @Test
    void pvphqSpellsTiersEitherWay() {
        assertEquals("HT3", DirectLists.pvphqRank("High Tier 3"));
        assertEquals("LT2", DirectLists.pvphqRank("lt2"));
        assertNull(DirectLists.pvphqRank("ST6"));
    }

    @Test
    void kitNamesMatchTheServer() {
        assertEquals("crystal", DirectLists.canonicalKit("vanilla"));
        assertEquals("spear", DirectLists.canonicalKit("SPEAR_MACE"));
        assertEquals("neth_pot", DirectLists.canonicalKit("Netherite-Pot"));
        assertEquals("brand_new", DirectLists.canonicalKit("brand-new"));
    }
}
