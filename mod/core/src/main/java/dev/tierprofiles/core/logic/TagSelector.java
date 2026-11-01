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
        SurfaceSettings surfaceSettings = settings.surface(surface);
        Filters filters = settings.filters;

        if (profile == null || !surfaceSettings.enabled) {
            return List.of();
        }
        if (context.isSelf() && filters.hideOwnTag) {
            return List.of();
        }

        List<Tag> tags = new ArrayList<>();
        for (TierList list : filters.listPriority) {
            if (tags.size() >= surfaceSettings.maxTags) {
                break;
            }
            if (!filters.isEnabled(list)) {
                continue;
            }
            Tag tag = bestOn(list, profile.list(list), surfaceSettings.minimum(), filters, context);
            if (tag != null) {
                tags.add(tag);
            }
        }

        if (tags.isEmpty() && !filters.hideUnranked && isKnownUnranked(profile, filters)) {
            return List.of(Tag.UNRANKED);
        }
        return tags;
    }

    private static Tag bestOn(TierList list, ListProfile listProfile, Tier surfaceMinimum, Filters filters, Context context) {
        if (!listProfile.isOk()) {
            return null;
        }

        Tier listMinimum = filters.listMinimum(list);
        TierEntry best = null;

        for (TierEntry entry : listProfile.tiers()) {
            if (filters.hideRetired && entry.retired()) {
                continue;
            }
            if (!kitCounts(entry.kit(), filters, context)) {
                continue;
            }
            Tier shown = entry.shown(filters.showPeak);
            if (surfaceMinimum != null && !shown.isAtLeast(surfaceMinimum)) {
                continue;
            }
            if (listMinimum != null && !shown.isAtLeast(listMinimum)) {
                continue;
            }
            if (best == null || isBetter(entry, best, filters)) {
                best = entry;
            }
        }

        if (best == null) {
            return null;
        }
        return new Tag(list, best.kit(), best.shown(filters.showPeak), best.retired(), listProfile.region());
    }

    private static boolean kitCounts(String kit, Filters filters, Context context) {
        return switch (filters.kitMode) {
            case HIGHEST -> true;
            case SELECTED -> filters.selectedKits.contains(kit);
            case AUTO -> context.detectedKit() == null || context.detectedKit().equals(kit);
        };
    }

    /** Stronger tier wins, then an active tier beats a retired one, then your kit order. */
    private static boolean isBetter(TierEntry candidate, TierEntry current, Filters filters) {
        int candidateScore = candidate.shown(filters.showPeak).score();
        int currentScore = current.shown(filters.showPeak).score();
        if (candidateScore != currentScore) {
            return candidateScore < currentScore;
        }
        if (candidate.retired() != current.retired()) {
            return current.retired();
        }
        if (filters.kitMode == Filters.KitMode.SELECTED) {
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
