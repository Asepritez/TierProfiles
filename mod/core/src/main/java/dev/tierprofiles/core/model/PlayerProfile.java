package dev.tierprofiles.core.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public record PlayerProfile(UUID uuid, String name, Map<TierList, ListProfile> lists) {
    /** Lists the server didn't mention count as unavailable, which is the honest default. */
    public ListProfile list(TierList list) {
        ListProfile found = lists.get(list);
        return found != null ? found : ListProfile.unavailable("missing");
    }

    public static PlayerProfile empty(UUID uuid, String name) {
        return new PlayerProfile(uuid, name, new EnumMap<>(TierList.class));
    }

    /** First region any list knows, trying lists in the given order. */
    public String region(Iterable<TierList> order) {
        for (TierList list : order) {
            String region = list(list).region();
            if (region != null) {
                return region;
            }
        }
        return null;
    }
}
