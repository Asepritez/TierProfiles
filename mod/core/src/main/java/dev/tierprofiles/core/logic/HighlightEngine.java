package dev.tierprofiles.core.logic;

import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierEntry;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Filters;
import dev.tierprofiles.core.settings.HighlightRule;
import java.util.List;

/**
 * Runs name highlight rules from top to bottom, and the first match wins.
 *
 * Rules share the retired and peak settings with tags, because those change what a
 * player's tier means. They ignore minimum-tier filters: hiding a tag is a display
 * choice, and it shouldn't stop a rule from seeing that the tier exists.
 */
public final class HighlightEngine {
    private HighlightEngine() {
    }

    public static NameStyle evaluate(PlayerProfile profile, List<HighlightRule> rules, Filters filters) {
        if (profile == null) {
            return NameStyle.NONE;
        }
        for (HighlightRule rule : rules) {
            if (rule.enabled && matches(rule, profile, filters)) {
                return NameStyle.of(rule);
            }
        }
        return NameStyle.NONE;
    }

    private static boolean matches(HighlightRule rule, PlayerProfile profile, Filters filters) {
        Tier minimum = rule.minimum();
        if (minimum == null) {
            return false;
        }

        for (TierList list : filters.listPriority) {
            if (!filters.isEnabled(list) || (rule.list != null && rule.list != list)) {
                continue;
            }
            if (hasTierAtLeast(profile.list(list), minimum, rule.kit, filters)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTierAtLeast(ListProfile listProfile, Tier minimum, String kit, Filters filters) {
        for (TierEntry entry : listProfile.tiers()) {
            if (filters.hideRetired && entry.retired()) {
                continue;
            }
            if (kit != null && !kit.equals(entry.kit())) {
                continue;
            }
            if (entry.shown(filters.showPeak).isAtLeast(minimum)) {
                return true;
            }
        }
        return false;
    }
}
