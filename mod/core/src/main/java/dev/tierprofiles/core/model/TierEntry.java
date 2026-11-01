package dev.tierprofiles.core.model;

/**
 * A player's standing in one kit on one list.
 *
 * @param peak     best rank held, or null when the list doesn't say
 * @param attained unix seconds, or null when the list doesn't say
 * @param rating   PVPHQ only, otherwise null
 * @param games    PVPHQ only, otherwise null
 */
public record TierEntry(String kit, Tier rank, Tier peak, Long attained, boolean retired, Integer rating, Integer games) {
    /** The tier to show. With no known peak, the current rank is the best we have. */
    public Tier shown(boolean usePeak) {
        return usePeak && peak != null ? peak : rank;
    }
}
