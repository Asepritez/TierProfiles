package dev.tierprofiles.core.logic;

import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Filters;

/**
 * The headline at the top of a card.
 *
 * @param bestPosition the player's best leaderboard position on any list, or null
 * @param bestList     the list that position is on, shown beside it so a beta list is
 *                     labelled as one
 * @param totalPoints  points added up across every list
 */
public record CardSummary(Integer bestPosition, TierList bestList, long totalPoints) {
    public static CardSummary of(PlayerProfile profile, Filters filters) {
        Integer best = null;
        TierList bestList = null;
        double points = 0;

        // Priority order, so the list you trust most wins a tie.
        for (TierList list : filters.listPriority) {
            if (!filters.isEnabled(list)) {
                continue;
            }
            ListProfile result = profile.list(list);
            if (!result.isOk()) {
                continue;
            }
            if (result.points() != null) {
                points += result.points();
            }
            Integer position = result.overall();
            if (position != null && position > 0 && (best == null || position < best)) {
                best = position;
                bestList = list;
            }
        }
        return new CardSummary(best, bestList, Math.round(points));
    }
}
