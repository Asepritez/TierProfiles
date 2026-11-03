package dev.tierprofiles.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;

/** Finding players by part of their name. */
class PlayerSearchTest {
    private static final String MCPVP = """
            {"players": [
              {"rank": 46154, "uuid": "835b345e23d8408da9313adee61ca1d4", "name": "Hoseinplays"},
              {"rank": 120, "uuid": "11111111222233334444555555555555", "name": "HoseinPvP"},
              {"rank": 9, "uuid": "aaaaaaaabbbbccccddddeeeeeeeeeeee", "name": "SomeoneElse"}]}""";

    private static final class Sites implements Transport {
        final ConcurrentLinkedQueue<String> asked = new ConcurrentLinkedQueue<>();
        final Map<String, String> pages;

        Sites(Map<String, String> pages) {
            this.pages = pages;
        }

        @Override
        public String send(String method, String url, String body, String bearer) throws IOException {
            asked.add(url);
            for (Map.Entry<String, String> page : pages.entrySet()) {
                if (url.contains(page.getKey())) {
                    return page.getValue();
                }
            }
            throw new StatusException(404);
        }
    }

    private static List<String> names(List<PlayerSearch.Result> results) {
        return results.stream().map(PlayerSearch.Result::name).toList();
    }

    @Test
    void partOfANameFindsEveryMatchingPlayer() {
        Sites sites = new Sites(Map.of("mcpvp.com", MCPVP));
        List<PlayerSearch.Result> results = new PlayerSearch(sites, Runnable::run, null).searchNow("Hosein");
        assertEquals(List.of("Hoseinplays", "HoseinPvP"), names(results), "a McPvP result that doesn't contain the text is left out");
        assertEquals("#46154 on McPvP", results.getFirst().detail());
    }

    @Test
    void anExactNameComesFirstEvenIfMcPvpDoesntKnowThem() {
        Sites sites = new Sites(Map.of(
                "mcpvp.com", MCPVP,
                "api.mojang.com/users/profiles/minecraft/HoseinX", "{\"id\":\"99999999888877776666555555555555\",\"name\":\"HoseinX\"}"));
        List<PlayerSearch.Result> results = new PlayerSearch(sites, Runnable::run, null).searchNow("HoseinX");
        assertEquals("HoseinX", results.getFirst().name());
        assertEquals("Exact name", results.getFirst().detail(), "an exact name McPvP doesn't know says so, since it may not be tier tested");
    }

    @Test
    void anExactNameMcPvpAlsoKnowsAppearsOnceWithItsRank() {
        Sites sites = new Sites(Map.of(
                "mcpvp.com", MCPVP,
                "api.mojang.com/users/profiles/minecraft/Hoseinplays", "{\"id\":\"835b345e23d8408da9313adee61ca1d4\",\"name\":\"Hoseinplays\"}"));
        List<PlayerSearch.Result> results = new PlayerSearch(sites, Runnable::run, null).searchNow("Hoseinplays");
        assertEquals(List.of("Hoseinplays"), names(results));
        assertEquals("#46154 on McPvP", results.getFirst().detail());
    }

    @Test
    void aListBeingDownStillLeavesTheOthers() {
        Sites sites = new Sites(Map.of("api.mojang.com/users/profiles/minecraft/Swight", "{\"id\":\"ebd7af32759e41e2b2279eeb8576d609\",\"name\":\"Swight\"}"));
        assertEquals(List.of("Swight"), names(new PlayerSearch(sites, Runnable::run, null).searchNow("Swight")));
    }

    @Test
    void tooShortOrOddTextIsNotSentAnywhere() {
        Sites sites = new Sites(Map.of("mcpvp.com", MCPVP));
        PlayerSearch search = new PlayerSearch(sites, Runnable::run, null);
        assertTrue(search.searchNow("Ho").isEmpty());
        assertTrue(search.searchNow("Hosein plays").isEmpty());
        assertTrue(search.searchNow("../../x").isEmpty());
        assertTrue(sites.asked.isEmpty(), "nothing should be asked for text that can't be a name");
        assertFalse(PlayerSearch.isSearchable("a".repeat(17)));
    }
}
