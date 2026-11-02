package dev.tierprofiles.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Global;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ProfileServiceTest {
    /** A pretend server. Set `reply` to choose what each request gets back. */
    private static final class FakeServer implements Transport {
        final List<String> urls = new ArrayList<>();
        final List<JsonObject> bodies = new ArrayList<>();
        Function<JsonObject, String> reply = FakeServer::allRanked;
        boolean down = false;

        @Override
        public String send(String method, String url, String body, String bearer) throws IOException {
            urls.add(url);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            bodies.add(json);
            if (down) {
                throw new IOException("connection refused");
            }
            return reply.apply(json);
        }

        static String allRanked(JsonObject body) {
            return profiles(body, """
                    {"status": "ok", "region": "EU", "tiers": [{"kit": "sword", "rank": "HT3", "retired": false}]}""");
        }

        static String profiles(JsonObject body, String mctiers) {
            List<String> out = new ArrayList<>();
            body.getAsJsonArray("players").forEach(player -> {
                String uuid = player.getAsJsonObject().get("uuid").getAsString();
                out.add("{\"uuid\":\"" + uuid + "\",\"lists\":{\"mctiers\":" + mctiers
                        + ",\"mcpvp\":{\"status\":\"unranked\"},\"pvptiers\":{\"status\":\"unranked\"}"
                        + ",\"subtiers\":{\"status\":\"unranked\"}"
                        + ",\"pvphq\":{\"status\":\"unavailable\",\"reason\":\"timeout\"}}}");
            });
            return "{\"profiles\":[" + String.join(",", out) + "]}";
        }
    }

    private final FakeServer server = new FakeServer();
    private final long[] clock = {1_000_000};
    private final Global global = new Global();
    private final ProfileService service = new ProfileService(server, () -> global, () -> clock[0]);

    ProfileServiceTest() {
        global.noticeSeen = true;
        global.source = Global.Source.SERVER;
    }

    /** Version 4 UUIDs, as real accounts have. */
    private static UUID player(int number) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012d", number));
    }

    private void lookUp(int... numbers) {
        for (int number : numbers) {
            service.request(player(number), "Player" + number);
        }
    }

    @Test
    void askingNeverSendsAnythingUntilTheBackgroundStep() {
        lookUp(1);
        assertTrue(server.urls.isEmpty());
        assertNull(service.profile(player(1)));
    }

    @Test
    void fillsTheCacheInTheBackground() {
        lookUp(1);
        service.pump();
        assertEquals(ListStatus.OK, service.profile(player(1)).list(TierList.MCTIERS).status());
    }

    @Test
    void sendsToTheConfiguredServer() {
        global.serverUrl = "https://example.com";
        lookUp(1);
        service.pump();
        assertEquals("https://example.com/v1/profiles", server.urls.getFirst());
    }

    @Test
    void sendsOnlyUuidsAndNames() {
        lookUp(1);
        service.pump();
        JsonObject body = server.bodies.getFirst();
        assertEquals(1, body.size());
        JsonObject sent = body.getAsJsonArray("players").get(0).getAsJsonObject();
        assertEquals(java.util.Set.of("uuid", "name"), sent.keySet());
        assertEquals("Player1", sent.get("name").getAsString());
        assertEquals(player(1).toString(), sent.get("uuid").getAsString());
    }

    @Test
    void batchesEightAtATime() {
        for (int i = 0; i < 20; i++) {
            lookUp(i);
        }
        service.pump();
        assertEquals(1, server.urls.size());
        assertEquals(8, server.bodies.getFirst().getAsJsonArray("players").size());
        assertEquals(12, service.queueSize());

        service.pump();
        service.pump();
        assertEquals(0, service.queueSize());
        assertEquals(3, server.urls.size());
    }

    @Test
    void skipsUuidsThatArentVersionFour() {
        UUID npc = UUID.fromString("00000000-0000-2000-8000-000000000001");
        UUID bedrock = UUID.fromString("00000000-0000-0000-0009-01f1e3339ed4");
        service.request(npc, "CitizenNPC");
        service.request(bedrock, "BedrockGuy");
        assertEquals(0, service.queueSize());
        assertFalse(ProfileService.isLookupEligible(null));
    }

    @Test
    void theOffSwitchStopsEverything() {
        global.lookupsEnabled = false;
        lookUp(1);
        assertEquals(0, service.queueSize());
        service.pump();
        assertTrue(server.urls.isEmpty());
    }

    @Test
    void turningItOffDropsWhatWasQueued() {
        lookUp(1);
        global.lookupsEnabled = false;
        service.pump();
        assertTrue(server.urls.isEmpty());
        assertEquals(0, service.queueSize());
    }

    @Test
    void nothingIsSentBeforeThePrivacyNoticeHasBeenSeen() {
        global.noticeSeen = false;
        lookUp(1);
        service.pump();
        assertTrue(server.urls.isEmpty());
    }

    @Test
    void doesntAskAgainWhileFresh() {
        lookUp(1);
        service.pump();
        lookUp(1);
        service.pump();
        assertEquals(1, server.urls.size());
    }

    @Test
    void asksAgainOnceStaleButKeepsShowingTheOldAnswer() {
        lookUp(1);
        service.pump();
        clock[0] += ProfileService.FRESH_MILLIS + 1;

        assertNotNull(service.profile(player(1)));
        lookUp(1);
        service.pump();
        assertEquals(2, server.urls.size());
    }

    @Test
    void repeatsInTheQueueAreSentOnce() {
        lookUp(1, 1, 1);
        assertEquals(1, service.queueSize());
    }

    @Test
    void urgentGoesFirst() {
        for (int i = 0; i < 10; i++) {
            lookUp(i);
        }
        service.requestUrgent(player(9), "Player9");
        service.pump();
        String first = server.bodies.getFirst().getAsJsonArray("players").get(0).getAsJsonObject().get("uuid").getAsString();
        assertEquals(player(9).toString(), first);
        assertEquals(2, service.queueSize());
    }

    @Test
    void aDownServerBacksOffAndDoesntHammer() {
        server.down = true;
        lookUp(1);
        service.pump();
        service.pump();
        service.pump();
        assertEquals(1, server.urls.size());

        clock[0] += ProfileService.FIRST_BACKOFF_MILLIS;
        service.pump();
        assertEquals(2, server.urls.size());

        service.pump();
        clock[0] += ProfileService.FIRST_BACKOFF_MILLIS;
        service.pump();
        assertEquals(2, server.urls.size());
    }

    @Test
    void backoffHasACeiling() {
        server.down = true;
        lookUp(1);
        for (int i = 0; i < 30; i++) {
            service.pump();
            clock[0] += ProfileService.MAX_BACKOFF_MILLIS;
        }
        assertEquals(30, server.urls.size());
    }

    @Test
    void aFailedBatchIsRetriedAndRecovers() {
        server.down = true;
        lookUp(1);
        service.pump();
        assertNull(service.profile(player(1)));

        server.down = false;
        clock[0] += ProfileService.MAX_BACKOFF_MILLIS;
        service.pump();
        assertNotNull(service.profile(player(1)));
    }

    @Test
    void garbageFromTheServerStoresNothing() {
        server.reply = body -> "{\"nope\": 1}";
        lookUp(1);
        service.pump();
        assertNull(service.profile(player(1)));

        server.reply = body -> "{\"profiles\": [\"junk\", {\"uuid\": \"banana\"}]}";
        lookUp(2);
        service.pump();
        assertNull(service.profile(player(2)));
    }

    @Test
    void aBusyListIsRetriedSoon() {
        server.reply = body -> FakeServer.profiles(body, "{\"status\":\"unavailable\",\"reason\":\"busy\"}");
        lookUp(1);
        service.pump();
        clock[0] += ProfileService.RETRY_BUSY_MILLIS + 1;
        lookUp(1);
        service.pump();
        assertEquals(2, server.urls.size());
    }

    @Test
    void aFailedListIsRetriedSoonerThanAGoodAnswer() {
        server.reply = body -> FakeServer.profiles(body, "{\"status\":\"unavailable\",\"reason\":\"timeout\"}");
        lookUp(1);
        service.pump();
        clock[0] += ProfileService.RETRY_FAILED_MILLIS + 1;
        lookUp(1);
        service.pump();
        assertEquals(2, server.urls.size());
    }

    @Test
    void pvphqBeingDownDoesntCauseConstantRelookups() {
        lookUp(1);
        service.pump();
        clock[0] += ProfileService.RETRY_FAILED_MILLIS + 1;
        lookUp(1);
        service.pump();
        assertEquals(1, server.urls.size());
    }

    @Test
    void aListGoingDownKeepsTheLastGoodAnswer() {
        lookUp(1);
        service.pump();
        clock[0] += ProfileService.FRESH_MILLIS + 1;

        server.reply = body -> FakeServer.profiles(body, "{\"status\":\"unavailable\",\"reason\":\"timeout\"}");
        lookUp(1);
        service.pump();

        assertEquals(1, service.profile(player(1)).list(TierList.MCTIERS).tiers().size());
    }

    @Test
    void aRealChangeToUnrankedDoesReplaceTheOldAnswer() {
        lookUp(1);
        service.pump();
        clock[0] += ProfileService.FRESH_MILLIS + 1;

        server.reply = body -> FakeServer.profiles(body, "{\"status\":\"unranked\"}");
        lookUp(1);
        service.pump();

        assertEquals(ListStatus.UNRANKED, service.profile(player(1)).list(TierList.MCTIERS).status());
    }

    @Test
    void clearForgetsEverything() {
        lookUp(1);
        service.pump();
        lookUp(2);
        service.clear();
        assertNull(service.profile(player(1)));
        assertEquals(0, service.queueSize());
    }

    @Test
    void theCacheDoesntGrowForever() {
        for (int i = 0; i < ProfileService.MAX_CACHED + 100; i++) {
            service.request(player(i), "P");
            if (i % ProfileService.BATCH_SIZE == 0) {
                service.pump();
                clock[0] += 1;
            }
        }
        while (service.queueSize() > 0) {
            service.pump();
        }
        int held = 0;
        for (int i = 0; i < ProfileService.MAX_CACHED + 100; i++) {
            held += service.profile(player(i)) != null ? 1 : 0;
        }
        assertEquals(ProfileService.MAX_CACHED, held);
    }

    @Test
    void theQueueDoesntGrowForever() {
        for (int i = 0; i < ProfileService.MAX_QUEUED * 2; i++) {
            lookUp(i);
        }
        assertEquals(ProfileService.MAX_QUEUED, service.queueSize());
    }

    @Test
    void backgroundThreadActuallyRuns() throws InterruptedException {
        lookUp(1);
        service.start();
        try {
            for (int i = 0; i < 100 && service.profile(player(1)) == null; i++) {
                Thread.sleep(20);
            }
        } finally {
            service.stop();
        }
        assertNotNull(service.profile(player(1)));
    }
}
