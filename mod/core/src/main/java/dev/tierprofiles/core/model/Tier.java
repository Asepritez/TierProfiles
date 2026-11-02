package dev.tierprofiles.core.model;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One rank, such as HT3. Lists disagree on how many there are: most have HT and
 * LT on levels 1 to 5, while McPvP adds MT and ST and goes down to level 6.
 *
 * To compare them all, every tier gets a score on one shared scale, where lower
 * is stronger: HT1=0, MT1=1, LT1=2, ST1=3, HT2=4 and so on. So LT3 (10) beats HT4
 * (12), and "LT3 and above" is simply "score of 10 or less".
 */
public record Tier(Band band, int level) {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 6;

    /** Strongest first. */
    public static final Comparator<Tier> STRONGEST_FIRST = Comparator.comparingInt(Tier::score);

    /** The ten tiers every list agrees on, which are the ones that get a colour. */
    public static final List<Tier> STANDARD = List.of(
            of(Band.HT, 1), of(Band.LT, 1), of(Band.HT, 2), of(Band.LT, 2), of(Band.HT, 3),
            of(Band.LT, 3), of(Band.HT, 4), of(Band.LT, 4), of(Band.HT, 5), of(Band.LT, 5));

    public Tier {
        if (band == null || level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("bad tier: " + band + level);
        }
    }

    public static Tier of(Band band, int level) {
        return new Tier(band, level);
    }

    /** Reads "HT3", ignoring case. Returns null for anything else, never throws. */
    public static Tier parse(String text) {
        if (text == null || text.length() != 3) {
            return null;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        Band band;
        try {
            band = Band.valueOf(upper.substring(0, 2));
        } catch (IllegalArgumentException e) {
            return null;
        }
        int level = upper.charAt(2) - '0';
        return level >= MIN_LEVEL && level <= MAX_LEVEL ? new Tier(band, level) : null;
    }

    /** Position on the shared scale. Lower is stronger. */
    public int score() {
        return (level - 1) * Band.values().length + band.ordinal();
    }

    public boolean isAtLeast(Tier other) {
        return score() <= other.score();
    }

    public String label() {
        return band.name() + level;
    }

    public String longLabel() {
        return band.longName() + " Tier " + level;
    }

    @Override
    public String toString() {
        return label();
    }
}
