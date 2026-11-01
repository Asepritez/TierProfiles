package dev.tierprofiles.core.model;

/** The MCTiers title ladder, highest first. */
public enum Title {
    GRANDMASTER("Grandmaster", 400),
    MASTER("Master", 250),
    ACE("Ace", 100),
    SPECIALIST("Specialist", 50),
    CADET("Cadet", 20),
    NOVICE("Novice", 10),
    ROOKIE("Rookie", 1);

    private final String displayName;
    private final int minimumPoints;

    Title(String displayName, int minimumPoints) {
        this.displayName = displayName;
        this.minimumPoints = minimumPoints;
    }

    public String displayName() {
        return displayName;
    }

    public int minimumPoints() {
        return minimumPoints;
    }

    /** Returns null below 1 point. */
    public static Title forPoints(double points) {
        for (Title title : values()) {
            if (points >= title.minimumPoints) {
                return title;
            }
        }
        return null;
    }

    /**
     * A title earned on a list. `official` is true for MCTiers. McPvP has no titles
     * of its own, so its title is ours, and the UI must say so.
     */
    public record Earned(Title title, TierList list, boolean official) {
        public String label() {
            return official ? title.displayName : title.displayName + " (TierProfiles title)";
        }
    }

    /** Returns null when the list has no title system, or no title has been reached. */
    public static Earned of(TierList list, ListProfile profile) {
        if (!list.hasTitles() || !profile.isOk() || profile.points() == null) {
            return null;
        }
        Title title = forPoints(profile.points());
        return title == null ? null : new Earned(title, list, list == TierList.MCTIERS);
    }
}
