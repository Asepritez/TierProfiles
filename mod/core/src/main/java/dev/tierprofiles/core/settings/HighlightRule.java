package dev.tierprofiles.core.settings;

import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierList;

/** "If the player's tier is at or above X on list Y, style their name like this." */
public final class HighlightRule {
    public boolean enabled = true;
    /** Null means any list. */
    public TierList list = null;
    /** Null means any kit. */
    public String kit = null;
    public String minimumTier = "HT3";
    public boolean ignoreRetired = false;
    /** Judge players by their best-ever tier rather than their current one. */
    public boolean usePeak = false;

    /** Name colour, or null to leave it alone. */
    public Integer color = 0xFF7777;
    /** When set along with `color`, the name fades from `color` to this. */
    public Integer gradientEnd = null;
    public boolean bold = false;
    public boolean glow = false;
    public boolean star = false;

    public static HighlightRule of(TierList list, String minimumTier, Integer color) {
        HighlightRule rule = new HighlightRule();
        rule.list = list;
        rule.minimumTier = minimumTier;
        rule.color = color;
        return rule;
    }

    public Tier minimum() {
        return Tier.parse(minimumTier);
    }

    void sanitize() {
        if (Tier.parse(minimumTier) == null) {
            minimumTier = "HT3";
        }
        if (color != null) {
            color &= 0xFFFFFF;
        }
        if (gradientEnd != null) {
            gradientEnd &= 0xFFFFFF;
        }
        if (kit != null && kit.isBlank()) {
            kit = null;
        }
    }
}
