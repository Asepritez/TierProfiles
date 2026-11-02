package dev.tierprofiles.ui;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.net.ProfileService;
import dev.tierprofiles.core.net.ProfileService.ListHealth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The truth behind "Unranked". The card treats a list that's down the same as one with
 * no result, so if anything looks wrong, this page says what really happened.
 */
final class Diagnostics {
    private Diagnostics() {
    }

    /** One list's real state, in plain words. */
    record Row(TierList list, String text, boolean healthy) {
    }

    static List<String> server() {
        ProfileService service = TierProfiles.profiles();
        return List.of(
                "Server: " + TierProfiles.config().global.serverUrl,
                "Last answer: " + service.lastStatus(),
                "Players known: " + service.knownPlayers() + ", waiting: " + service.queueSize(),
                "Card: " + TierProfiles.cosmetics().state().message());
    }

    static List<Row> lists() {
        Map<TierList, ListHealth> health = TierProfiles.profiles().health();
        List<Row> rows = new ArrayList<>();
        for (TierList list : TierList.values()) {
            ListHealth entry = health.get(list);
            int down = entry.count(ListStatus.UNAVAILABLE);
            int answered = entry.count(ListStatus.OK) + entry.count(ListStatus.UNRANKED);
            if (down > 0) {
                rows.add(new Row(list, "Down, " + reason(entry.lastReason()), false));
            } else if (answered == 0) {
                rows.add(new Row(list, "Not asked yet", true));
            } else {
                rows.add(new Row(list, "Online, " + entry.count(ListStatus.OK) + " of " + answered + " players ranked", true));
            }
        }
        return rows;
    }

    private static String reason(String reason) {
        if (reason == null) {
            return "no reason given";
        }
        if (reason.startsWith("http_")) {
            return "server replied " + reason.substring(5);
        }
        return switch (reason) {
            case "timeout" -> "not answering";
            case "network" -> "can't be reached";
            case "circuit_open" -> "resting after failures";
            case "busy" -> "our server was busy";
            case "bad_json", "bad_shape" -> "sent something unreadable";
            case "name_required", "name_too_short" -> "needs a longer name";
            case "search_truncated" -> "too many name matches";
            default -> reason;
        };
    }
}
