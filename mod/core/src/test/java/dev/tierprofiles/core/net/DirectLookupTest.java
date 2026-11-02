package dev.tierprofiles.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Global;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Looking players up straight from the lists: each list on its own, and kept between sessions. */
class DirectLookupTest {
    private static final UUID REAL = UUID.fromString("1246a0ec-662b-45de-bb0b-d68cb1cce800");
    /** Version 2, the kind servers use for made-up tab rows. */
    private static final UUID FAKE = UUID.fromString("00000000-0000-2000-8000-000000000001");

    /** Pretends to be all five lists. PVPHQ can be held back to play a slow list. */
    private static final class FakeLists implements Transport {
        final ConcurrentLinkedQueue<String> urls = new ConcurrentLinkedQueue<>();
        final CountDownLatch pvphq = new CountDownLatch(1);
        volatile boolean holdPvphq;

        @Override
        public String send(String method, String url, String body, String bearer) throws IOException {
            urls.add(url);
            if (url.contains("pvphq.com")) {
                if (holdPvphq) {
                    try {
                        pvphq.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return "{\"data\":{}}";
            }
            if (url.contains("mcpvp.com")) {
                return "{\"players\":[{\"uuid\":\"1246a0ec662b45debb0bd68cb1cce800\",\"name\":\"JokingDK\",\"kitRanks\":{\"sword\":\"HT2\"}}]}";
            }
            return "{\"region\":\"EU\",\"points\":10,\"overall\":5,\"rankings\":{\"sword\":{\"tier\":3,\"pos\":0,\"attained\":1,\"retired\":false}}}";
        }
    }

    private final FakeLists lists = new FakeLists();
    private final long[] clock = {System.currentTimeMillis()};
    private final Global global = new Global();
    private ProfileService service;

    @TempDir
    Path folder;

    DirectLookupTest() {
        global.noticeSeen = true;
    }

    private ProfileService service(Path file) {
        service = new ProfileService(lists, () -> global, () -> clock[0], file);
        return service;
    }

    @AfterEach
    void stop() {
        lists.pvphq.countDown();
        if (service != null) {
            service.stop();
        }
    }

    private static void waitFor(BooleanSupplier condition, String what) {
        long until = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < until, "timed out waiting: " + what);
            Thread.onSpinWait();
        }
    }

    @Test
    void aFastListShowsBeforeASlowOneAnswers() {
        ProfileService service = service(null);
        lists.holdPvphq = true;
        service.request(REAL, "JokingDK");
        service.pump();

        // The fast lists answer on their own threads, so wait for both rather than racing one against the other.
        waitFor(() -> service.profile(REAL) != null && service.profile(REAL).list(TierList.MCTIERS).isOk()
                && service.profile(REAL).list(TierList.MCPVP).isOk(), "MCTiers and McPvP");
        PlayerProfile early = service.profile(REAL);
        assertEquals("missing", early.list(TierList.PVPHQ).reason(), "PVPHQ is still being asked");

        lists.pvphq.countDown();
        waitFor(() -> service.queueSize() == 0, "every list to finish");
        assertEquals(ListStatus.UNRANKED, service.profile(REAL).list(TierList.PVPHQ).status());
    }

    @Test
    void aWholeLobbyIsAskedAtOnceNotInSlowGroups() {
        ProfileService service = service(null);
        for (int i = 0; i < 30; i++) {
            service.request(UUID.fromString(String.format("00000000-0000-4000-8000-%012d", i)), "Player" + i);
        }
        service.pump();
        waitFor(() -> service.queueSize() == 0, "the lobby");
        assertEquals(30, service.knownPlayers());
        assertEquals(30 * TierList.values().length, lists.urls.size());
    }

    @Test
    void savedTiersShowStraightAwayNextTime() throws IOException {
        Path file = folder.resolve("cache.json");
        ProfileService first = service(file);
        first.request(REAL, "JokingDK");
        first.pump();
        waitFor(() -> first.queueSize() == 0, "the lookup");
        first.save();
        first.stop();

        int asked = lists.urls.size();
        ProfileService next = service(file);
        next.load();
        assertNotNull(next.profile(REAL), "known before anything is asked");
        assertTrue(next.profile(REAL).list(TierList.SUBTIERS).isOk());

        next.request(REAL, "JokingDK");
        next.pump();
        assertEquals(asked, lists.urls.size(), "checked moments ago, so not asked again");
    }

    @Test
    void savedTiersFromLongAgoAreDropped() throws IOException {
        Path file = folder.resolve("cache.json");
        ProfileService first = service(file);
        first.request(REAL, "JokingDK");
        first.pump();
        waitFor(() -> first.queueSize() == 0, "the lookup");
        first.save();
        first.stop();

        clock[0] += ProfileService.DISK_MAX_AGE_MILLIS + 1;
        ProfileService later = service(file);
        later.load();
        assertNull(later.profile(REAL));
    }

    @Test
    void aMadeUpTabRowGetsTheRealPlayersTiers() {
        ProfileService service = service(null);
        service.requestByName(FAKE, List.of("not a name!", "JokingDK"));
        waitFor(() -> lists.urls.stream().anyMatch(url -> url.contains("mcpvp.com/tiers/search?q=JokingDK")), "the name search");
        waitFor(() -> {
            service.pump();
            return service.profile(FAKE) != null && service.queueSize() == 0;
        }, "the real player's tiers");
        assertEquals(REAL, service.profile(FAKE).uuid());
    }

    @Test
    void aNameNobodyHasLeavesTheRowAlone() {
        ProfileService service = service(null);
        service.requestByName(FAKE, List.of("Nobody123"));
        waitFor(() -> lists.urls.stream().anyMatch(url -> url.contains("q=Nobody123")), "the name search");
        service.pump();
        assertNull(service.profile(FAKE));
    }

    @Test
    void namesAreOnlyLookedUpWithLookupsOn() {
        global.lookupsEnabled = false;
        ProfileService service = service(null);
        service.requestByName(FAKE, List.of("JokingDK"));
        service.request(REAL, "JokingDK");
        service.pump();
        assertTrue(lists.urls.isEmpty());
    }
}
