package dev.tierprofiles.core.settings;

import dev.tierprofiles.core.model.Band;
import dev.tierprofiles.core.model.Tier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in tier colours. A palette defines the ten tiers all lists share, and
 * McPvP's extra tiers are worked out from those so they always look related.
 */
public enum Palette {
    DEFAULT("Default", 0xFFCF4A, 0xE6B422, 0xD0D6E0, 0x9FA8B8, 0xE08A4F, 0xB8703F, 0x7FB069, 0x5E8A4E, 0x8A8F98, 0x6A6F78),
    HEAT("Heat", 0xFF2D2D, 0xFF5A1F, 0xFF8A1F, 0xFFA92E, 0xFFC640, 0xFFDA62, 0xFFE78C, 0xFFEFAD, 0xCFC7A8, 0xA8A28C),
    MONO("Mono", 0xFFFFFF, 0xEDEDED, 0xDADADA, 0xC6C6C6, 0xB2B2B2, 0x9E9E9E, 0x8A8A8A, 0x767676, 0x626262, 0x505050),
    // Built on the Okabe-Ito set, chosen so steps stay distinct with colour blindness.
    COLOURBLIND("Colourblind-safe", 0xF0E442, 0xE69F00, 0x56B4E9, 0x3A8FC4, 0xD55E00, 0xA84A00, 0x009E73, 0x00785A, 0xCC79A7, 0x9B5C80);

    private final String displayName;
    private final Map<String, Integer> colors = new HashMap<>();

    Palette(String displayName, int... standard) {
        this.displayName = displayName;
        List<Tier> tiers = Tier.STANDARD;
        for (int i = 0; i < tiers.size(); i++) {
            colors.put(tiers.get(i).label(), standard[i]);
        }
    }

    public String displayName() {
        return displayName;
    }

    public int color(Tier tier) {
        Integer exact = colors.get(tier.label());
        return exact != null ? exact : derived(tier);
    }

    private int derived(Tier tier) {
        int level = Math.min(tier.level(), 5);
        int high = colors.get(Tier.of(Band.HT, level).label());
        int low = colors.get(Tier.of(Band.LT, level).label());

        int base = switch (tier.band()) {
            case HT -> high;
            case MT -> Colors.mix(high, low, 0.5f);
            case LT -> low;
            case ST -> Colors.scale(low, 0.8f);
        };
        // Level 6 exists only on McPvP, and sits below everything else.
        return tier.level() > 5 ? Colors.scale(base, 0.75f) : base;
    }
}
