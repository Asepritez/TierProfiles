package dev.tierprofiles.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tierprofiles.core.net.CosmeticsService.SaveState;
import dev.tierprofiles.core.settings.Cosmetics;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CosmeticsServiceTest {
    private static final String TOKEN = "SECRET-ACCESS-TOKEN-FROM-THE-GAME";
    private static final UUID PLAYER = UUID.fromString("06ec3577-3299-45fa-bbdf-613b1f86c8ab");

    private record Call(String method, String url, String body, String bearer) {
    }

    private static final class FakeServer implements Transport {
        final List<Call> calls = new ArrayList<>();
        String cosmeticsReply = "{\"cosmetics\": null}";
        int putStatus = 200;
        int verifyStatus = 200;
        boolean down;

        @Override
        public String send(String method, String url, String body, String bearer) throws IOException {
            calls.add(new Call(method, url, body, bearer));
            if (down) {
                throw new IOException("connection refused");
            }
            if (url.endsWith("/v1/auth/challenge")) {
                return "{\"challenge\": \"abc.123.sig\", \"serverId\": \"abc\", \"expires\": 1}";
            }
            if (url.endsWith("/v1/auth/verify")) {
                if (verifyStatus != 200) {
                    throw new StatusException(verifyStatus);
                }
                return "{\"token\": \"our-short-token\", \"uuid\": \"x\", \"expires\": 1}";
            }
            if (method.equals("PUT")) {
                if (putStatus != 200) {
                    throw new StatusException(putStatus);
                }
                return "{}";
            }
            return cosmeticsReply;
        }
    }

    private final FakeServer server = new FakeServer();
    private final long[] clock = {1_000_000};
    private final List<String> joined = new ArrayList<>();
    private CosmeticsService.SessionJoiner joiner = serverId -> joined.add(serverId + ":" + TOKEN);
    private final CosmeticsService service = new CosmeticsService(server, () -> "http://server", id -> joiner.join(id), Runnable::run, () -> clock[0]);

    private Cosmetics look() {
        Cosmetics cosmetics = new Cosmetics().sanitize();
        cosmetics.background = "moon";
        return cosmetics;
    }

    @Test
    void savingFollowsTheJoinSteps() {
        service.save(look(), "Tester");

        assertEquals(SaveState.SAVED, service.state());
        assertEquals("abc:" + TOKEN, joined.getFirst());
        assertEquals(List.of("POST", "POST", "PUT"), server.calls.stream().map(Call::method).toList());
        assertEquals("our-short-token", server.calls.get(2).bearer());
    }

    /** The promise in the README: the access token goes only to Mojang. */
    @Test
    void theAccessTokenNeverReachesOurServer() {
        service.save(look(), "Tester");
        for (Call call : server.calls) {
            assertFalse(String.valueOf(call.url()).contains(TOKEN));
            assertFalse(String.valueOf(call.body()).contains(TOKEN));
            assertFalse(String.valueOf(call.bearer()).contains(TOKEN));
        }
    }

    @Test
    void onlyTheCardIsSent() {
        service.save(look(), "Tester");
        Call put = server.calls.get(2);
        assertTrue(put.body().contains("\"background\":\"moon\""));
        assertFalse(put.body().contains("Tester"));
    }

    @Test
    void aTokenIsReusedUntilItExpires() {
        service.save(look(), "Tester");
        service.save(look(), "Tester");
        assertEquals(1, joined.size());

        clock[0] += 13 * 60 * 1000;
        service.save(look(), "Tester");
        assertEquals(2, joined.size());
    }

    @Test
    void anOfflineAccountCantSave() {
        joiner = serverId -> {
            throw new IOException("invalid session");
        };
        service.save(look(), "Cracked");
        assertEquals(SaveState.NOT_SIGNED_IN, service.state());
        assertEquals(1, server.calls.size());
    }

    @Test
    void mojangSayingNoCountsAsNotSignedIn() {
        server.verifyStatus = 403;
        service.save(look(), "Tester");
        assertEquals(SaveState.NOT_SIGNED_IN, service.state());
    }

    @Test
    void savingTooOftenSaysSo() {
        server.putStatus = 429;
        service.save(look(), "Tester");
        assertEquals(SaveState.TOO_SOON, service.state());
    }

    @Test
    void aRefusedCardSaysSo() {
        server.putStatus = 400;
        service.save(look(), "Tester");
        assertEquals(SaveState.REFUSED, service.state());
    }

    @Test
    void anExpiredTokenAsksToSignInAgainNextTime() {
        service.save(look(), "Tester");
        server.putStatus = 401;
        service.save(look(), "Tester");
        assertEquals(SaveState.NOT_SIGNED_IN, service.state());

        server.putStatus = 200;
        service.save(look(), "Tester");
        assertEquals(SaveState.SAVED, service.state());
        assertEquals(2, joined.size());
    }

    @Test
    void aDownServerSaysSo() {
        server.down = true;
        service.save(look(), "Tester");
        assertEquals(SaveState.OFFLINE, service.state());
    }

    @Test
    void fetchesAndSanitizesOtherPlayersCards() {
        server.cosmeticsReply = "{\"cosmetics\": {\"background\": \"moon\", \"border\": \"evil\"}}";
        service.request(PLAYER);

        Cosmetics card = service.get(PLAYER);
        assertNotNull(card);
        assertEquals("moon", card.background);
        assertEquals("thin", card.border);
    }

    @Test
    void aPlayerWithNoCardHasNothingCached() {
        service.request(PLAYER);
        assertNull(service.get(PLAYER));
    }

    @Test
    void doesntAskAgainStraightAway() {
        service.request(PLAYER);
        service.request(PLAYER);
        assertEquals(1, server.calls.size());

        clock[0] += 11 * 60 * 1000;
        service.request(PLAYER);
        assertEquals(2, server.calls.size());
    }

    @Test
    void junkFromTheServerIsIgnored() {
        server.cosmeticsReply = "<html>";
        service.request(PLAYER);
        assertNull(service.get(PLAYER));
    }
}
