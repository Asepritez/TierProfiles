package dev.tierprofiles.ui;

import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierEntry;
import dev.tierprofiles.core.model.TierList;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Invented players for the settings previews. No real person's tiers appear here. */
final class SamplePlayers {
    private static final List<PlayerProfile> ALL = new ArrayList<>();

    static {
        add("OneTap_Oli", "sword", "HT1", false);
        add("SoupGhost", "pot", "LT2", false);
        add("Maceling", "mace", "HT3", false);
        add("ByteBow", "bow", "LT3", true);
        add("PearlPanic", "crystal", "HT4", false);
        add("KitSmith", "sword", "LT4", false);
        add("lowroll_", "pot", "HT5", false);
    }

    private SamplePlayers() {
    }

    private static void add(String name, String kit, String rank, boolean retired) {
        Tier tier = Tier.parse(rank);
        TierEntry entry = new TierEntry(kit, tier, tier, null, retired, null, null);
        ListProfile list = new ListProfile(ListStatus.OK, null, "EU", null, 50.0, 100, List.of(entry));
        Map<TierList, ListProfile> lists = new EnumMap<>(TierList.class);
        lists.put(TierList.MCTIERS, list);
        ALL.add(new PlayerProfile(UUID.nameUUIDFromBytes(name.getBytes()), name, lists));
    }

    static List<PlayerProfile> all() {
        return ALL;
    }
}
