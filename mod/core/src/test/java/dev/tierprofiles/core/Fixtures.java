package dev.tierprofiles.core;

import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierEntry;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Settings;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Short ways to build test players. */
public final class Fixtures {
    public static final UUID PLAYER = UUID.fromString("1246a0ec-662b-45de-bb0b-d68cb1cce800");

    private Fixtures() {
    }

    public static TierEntry entry(String kit, String rank) {
        return new TierEntry(kit, Tier.parse(rank), null, null, false, null, null);
    }

    public static TierEntry entry(String kit, String rank, String peak, boolean retired) {
        return new TierEntry(kit, Tier.parse(rank), Tier.parse(peak), 1700000000L, retired, null, null);
    }

    public static ListProfile ok(TierEntry... entries) {
        return new ListProfile(ListStatus.OK, null, "EU", null, 100.0, 5, List.copyOf(Arrays.asList(entries)));
    }

    /** Build with pairs: player(TierList.PVPHQ, ok(...), TierList.MCPVP, ok(...)). */
    public static PlayerProfile player(Object... pairs) {
        Map<TierList, ListProfile> lists = new EnumMap<>(TierList.class);
        for (int i = 0; i < pairs.length; i += 2) {
            lists.put((TierList) pairs[i], (ListProfile) pairs[i + 1]);
        }
        return new PlayerProfile(PLAYER, "Tester", lists);
    }

    /** Fresh, sanitized defaults. */
    public static Settings settings() {
        return new Settings().sanitize();
    }
}
