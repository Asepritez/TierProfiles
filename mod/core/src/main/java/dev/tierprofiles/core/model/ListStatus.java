package dev.tierprofiles.core.model;

public enum ListStatus {
    /** Here are their tiers. */
    OK,
    /** The list answered, and this player isn't on it. */
    UNRANKED,
    /** We don't know. Must never be treated as unranked. */
    UNAVAILABLE
}
