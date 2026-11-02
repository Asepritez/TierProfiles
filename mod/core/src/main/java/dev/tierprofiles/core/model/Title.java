package dev.tierprofiles.core.model;

/** The title ladder MCTiers made popular, highest first. */
public enum Title {
    GRANDMASTER("Combat Grandmaster", 400),
    MASTER("Combat Master", 250),
    ACE("Combat Ace", 100),
    SPECIALIST("Combat Specialist", 50),
    CADET("Combat Cadet", 20),
    NOVICE("Combat Novice", 10),
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
     * A title earned on a list. None of the lists we read give titles themselves, so
     * every title is ours, and the UI must say so.
     */
    public record Earned(Title title, TierList list) {
        public String label() {
            return title.displayName + " (TierProfiles title)";
        }
    }

    /** Returns null when the list has no title system, or no title has been reached. */
    public static Earned of(TierList list, ListProfile profile) {
        if (!list.hasTitles() || !profile.isOk() || profile.points() == null) {
            return null;
        }
        Title title = forPoints(profile.points());
        return title == null ? null : new Earned(title, list);
    }
}
