package dev.tierprofiles.core.logic;

import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.TierEntry;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Filters;
import dev.tierprofiles.core.settings.HighlightRule;
import java.util.List;

/**
 * Runs name highlight rules from top to bottom, and the first match wins.
 *
 * Each rule says for itself whether retired tiers count and whether to use peaks.
 * Rules ignore minimum-tier filters: hiding a tag is a display choice, and it
 * shouldn't stop a rule from seeing that the tier exists.
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
        if (rule.minimum() == null) {
            return false;
        }

        for (TierList list : filters.listPriority) {
            if (!filters.isEnabled(list) || (rule.list != null && rule.list != list)) {
                continue;
            }
            if (hasMatchingTier(profile.list(list), rule, filters)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMatchingTier(ListProfile listProfile, HighlightRule rule, Filters filters) {
        for (TierEntry entry : listProfile.tiers()) {
            if (rule.ignoreRetired && entry.retired() || !filters.isKitShown(entry.kit())) {
                continue;
            }
            if (rule.kit != null && !rule.kit.equals(entry.kit())) {
                continue;
            }
            if (rule.accepts(entry.shown(rule.usePeak))) {
                return true;
            }
        }
        return false;
    }
}
