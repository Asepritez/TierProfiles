package dev.tierprofiles.core.logic;

import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierList;

/**
 * One tag picked for display. `tier` is null for the "Unranked" placeholder, which
 * is also the only case where `list` and `kit` are null.
 *
 * @param higherPeak set only when showing both tiers and the peak beats the current
 */
public record Tag(TierList list, String kit, Tier tier, Tier higherPeak, boolean retired, String region) {
    public static final Tag UNRANKED = new Tag(null, null, null, null, false, null);

    public boolean isUnranked() {
        return tier == null;
    }
}
