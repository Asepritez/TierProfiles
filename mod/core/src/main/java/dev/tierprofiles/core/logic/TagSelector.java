package dev.tierprofiles.core.logic;

import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierEntry;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Filters;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.core.settings.SurfaceSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * The filter engine: given a player and your settings, which tags should appear?
 *
 * Lists are visited in priority order and each offers at most one tag, its best
 * tier that survives every filter. The first `maxTags` offers win, which is what
 * makes list priority mean "who gets the only slot".
 */
public final class TagSelector {
    private TagSelector() {
    }

    /**
     * @param isSelf      whether this player is you
     * @param detectedKit the kit your inventory suggests, or null
     */
    public record Context(boolean isSelf, String detectedKit) {
        public static final Context NONE = new Context(false, null);
    }

    public static List<Tag> select(PlayerProfile profile, Surface surface, Settings settings, Context context) {
        SurfaceSettings rules = settings.surface(surface);
        Filters filters = settings.filters;

        if (profile == null || !rules.enabled) {
            return List.of();
        }
        if (context.isSelf() && rules.hideOwnTag) {
            return List.of();
        }

        List<Tag> tags = new ArrayList<>();
        for (TierList list : filters.listPriority) {
            if (tags.size() >= rules.maxTags) {
                break;
            }
            if (!filters.isEnabled(list)) {
                continue;
            }
            Tag tag = bestOn(list, profile.list(list), rules, filters, context);
            if (tag != null) {
                tags.add(tag);
            }
        }

        if (tags.isEmpty() && !rules.hideUnranked && isKnownUnranked(profile, filters)) {
            return List.of(Tag.UNRANKED);
        }
        return tags;
    }

    private static Tag bestOn(TierList list, ListProfile listProfile, SurfaceSettings rules, Filters filters, Context context) {
        if (!listProfile.isOk()) {
            return null;
        }

        boolean usePeak = rules.tierMode == SurfaceSettings.TierMode.PEAK;
        Tier surfaceMinimum = rules.minimum();
        Tier listMinimum = filters.listMinimum(list);
        TierEntry best = null;

        for (TierEntry entry : listProfile.tiers()) {
            if (rules.hideRetired && entry.retired() || !filters.isKitShown(entry.kit())) {
                continue;
            }
            if (!kitCounts(entry.kit(), rules, filters, context)) {
                continue;
            }
            Tier shown = entry.shown(usePeak);
            if (surfaceMinimum != null && !shown.isAtLeast(surfaceMinimum)) {
                continue;
            }
            if (listMinimum != null && !shown.isAtLeast(listMinimum)) {
                continue;
            }
            if (best == null || isBetter(entry, best, usePeak, rules, filters)) {
                best = entry;
            }
        }

        if (best == null) {
            return null;
        }
        Tier shown = best.shown(usePeak);
        Tier higherPeak = null;
        if (rules.tierMode == SurfaceSettings.TierMode.BOTH && best.peak() != null && !shown.isAtLeast(best.peak())) {
            higherPeak = best.peak();
        }
        return new Tag(list, best.kit(), shown, higherPeak, best.retired(), listProfile.region());
    }

    private static boolean kitCounts(String kit, SurfaceSettings rules, Filters filters, Context context) {
        return switch (rules.kitMode) {
            case HIGHEST -> true;
            case SELECTED -> filters.selectedKits.contains(kit);
            case AUTO -> context.detectedKit() == null || context.detectedKit().equals(kit);
        };
    }

    /** Stronger tier wins, then an active tier beats a retired one, then your kit order. */
    private static boolean isBetter(TierEntry candidate, TierEntry current, boolean usePeak, SurfaceSettings rules, Filters filters) {
        int candidateScore = candidate.shown(usePeak).score();
        int currentScore = current.shown(usePeak).score();
        if (candidateScore != currentScore) {
            return candidateScore < currentScore;
        }
        if (candidate.retired() != current.retired()) {
            return current.retired();
        }
        if (rules.kitMode == Filters.KitMode.SELECTED) {
            return filters.selectedKits.indexOf(candidate.kit()) < filters.selectedKits.indexOf(current.kit());
        }
        return false;
    }

    /**
     * Only claim "unranked" when an enabled list said so and none has tiers or
     * failed to answer. A list being down means we don't know.
     */
    private static boolean isKnownUnranked(PlayerProfile profile, Filters filters) {
        boolean anyUnranked = false;
        for (TierList list : filters.listPriority) {
            if (!filters.isEnabled(list)) {
                continue;
            }
            ListStatus status = profile.list(list).status();
            if (status != ListStatus.UNRANKED) {
                return false;
            }
            anyUnranked = true;
        }
        return anyUnranked;
    }
}
