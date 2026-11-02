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

    static List<String> lines() {
        ProfileService service = TierProfiles.profiles();
        List<String> lines = new ArrayList<>();
        lines.add("Server: " + TierProfiles.config().global.serverUrl);
        lines.add("Last answer: " + service.lastStatus());
        lines.add("Players known: " + service.knownPlayers() + ", waiting: " + service.queueSize());
        lines.add("Card: " + TierProfiles.cosmetics().state().message());
        lines.add("");

        Map<TierList, ListHealth> health = service.health();
        for (TierList list : TierList.values()) {
            ListHealth entry = health.get(list);
            lines.add(list.displayName() + ": " + entry.count(ListStatus.OK) + " ranked, "
                    + entry.count(ListStatus.UNRANKED) + " unranked, "
                    + entry.count(ListStatus.UNAVAILABLE) + " down"
                    + (entry.lastReason() == null ? "" : " (" + entry.lastReason() + ")"));
        }
        return lines;
    }
}
