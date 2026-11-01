package dev.tierprofiles.core.model;

public final class TimeAgo {
    private static final long MINUTE = 60;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long MONTH = 30 * DAY;
    private static final long YEAR = 365 * DAY;

    private TimeAgo() {
    }

    public static String format(long thenSeconds, long nowSeconds) {
        long elapsed = Math.max(0, nowSeconds - thenSeconds);
        if (elapsed < MINUTE) {
            return "just now";
        }
        if (elapsed < HOUR) {
            return plural(elapsed / MINUTE, "minute");
        }
        if (elapsed < DAY) {
            return plural(elapsed / HOUR, "hour");
        }
        if (elapsed < MONTH) {
            return plural(elapsed / DAY, "day");
        }
        if (elapsed < YEAR) {
            return plural(elapsed / MONTH, "month");
        }
        return plural(elapsed / YEAR, "year");
    }

    private static String plural(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s") + " ago";
    }
}
