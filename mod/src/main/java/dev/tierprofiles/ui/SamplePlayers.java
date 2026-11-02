package dev.tierprofiles.ui;

import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierEntry;
import dev.tierprofiles.core.model.TierList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An invented player for the Live Stage. No real person's tiers appear here. */
final class SamplePlayers {
    private static final long ONE_YEAR = 365L * 24 * 60 * 60;
    private static final PlayerProfile SHOWCASE = showcase();

    private SamplePlayers() {
    }

    private static TierEntry tier(String kit, String rank, String peak, boolean retired, long age) {
        long now = System.currentTimeMillis() / 1000;
        return new TierEntry(kit, Tier.parse(rank), Tier.parse(peak), now - age, retired, null, null);
    }

    private static ListProfile list(int position, double points, TierEntry... tiers) {
        return new ListProfile(ListStatus.OK, null, "EU", null, points, position, List.of(tiers));
    }

    private static PlayerProfile showcase() {
        Map<TierList, ListProfile> lists = new EnumMap<>(TierList.class);
        lists.put(TierList.MCPVP, list(12, 180, tier("smp", "HT1", null, false, ONE_YEAR / 12), tier("shield", "LT1", null, false, 0)));
        lists.put(TierList.PVPTIERS, list(175, 64,
                tier("crystal", "HT3", null, false, ONE_YEAR / 2),
                tier("smp", "LT3", "HT3", false, ONE_YEAR)));
        lists.put(TierList.SUBTIERS, list(160, 83, tier("dia_smp", "LT1", "LT1", true, ONE_YEAR), tier("bed", "LT3", null, false, ONE_YEAR / 4)));
        lists.put(TierList.PVPHQ, list(56, 102,
                tier("smp", "HT2", "HT2", true, ONE_YEAR / 3),
                tier("crystal", "LT2", "LT2", true, ONE_YEAR),
                tier("neth_pot", "HT3", null, false, ONE_YEAR / 6)));
        return new PlayerProfile(UUID.nameUUIDFromBytes("TierProfilesSample".getBytes()), "SampleSteve", lists);
    }

    static PlayerProfile showcasePlayer() {
        return SHOWCASE;
    }
}
