package dev.tierprofiles.core.model;

import java.util.List;

/** The lists we read. Their order here is the font's logo glyph order, so a new list goes on the end. */
public enum TierList {
    MCPVP("mcpvp", "McPvP", "MPV", true, true),
    PVPTIERS("pvptiers", "PvPTiers", "PVT", false, true),
    SUBTIERS("subtiers", "SubTiers", "SUB", false, true),
    PVPHQ("pvphq", "PVPHQ", "PHQ", false, false);

    /** PVPHQ comes first, then McPvP. MCTiers led until it shut down in Nov 2026. */
    public static final List<TierList> DEFAULT_PRIORITY = List.of(PVPHQ, MCPVP, PVPTIERS, SUBTIERS);

    private final String id;
    private final String displayName;
    private final String shortName;
    private final boolean beta;
    private final boolean titled;

    TierList(String id, String displayName, String shortName, boolean beta, boolean titled) {
        this.id = id;
        this.displayName = displayName;
        this.shortName = shortName;
        this.beta = beta;
        this.titled = titled;
    }

    /** The key used in the server's JSON and in icon file names. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** For tight spaces, such as a nametag with the logo off. */
    public String shortName() {
        return shortName;
    }

    /** Beta tiers can reset, so the UI labels them. */
    public boolean isBeta() {
        return beta;
    }

    /** Whether the title ladder (Combat Ace and so on) applies to this list's points. */
    public boolean hasTitles() {
        return titled;
    }

    public static TierList byId(String id) {
        for (TierList list : values()) {
            if (list.id.equals(id)) {
                return list;
            }
        }
        return null;
    }
}
