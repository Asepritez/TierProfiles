package dev.tierprofiles.core.model;

import java.util.List;

/** A player's standing on one list. Only an OK status carries any tiers. */
public record ListProfile(
        ListStatus status,
        String reason,
        String region,
        String country,
        Double points,
        Integer overall,
        List<TierEntry> tiers) {

    public static final ListProfile UNRANKED = new ListProfile(ListStatus.UNRANKED, null, null, null, null, null, List.of());

    public static ListProfile unavailable(String reason) {
        return new ListProfile(ListStatus.UNAVAILABLE, reason, null, null, null, null, List.of());
    }

    public boolean isOk() {
        return status == ListStatus.OK;
    }
}
