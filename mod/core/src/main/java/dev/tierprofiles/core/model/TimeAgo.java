package dev.tierprofiles.core.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TimeAgo {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

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

    /** A calendar date such as "19 Aug 2026", in your own time zone. */
    public static String date(long seconds) {
        return date(seconds, ZoneId.systemDefault());
    }

    static String date(long seconds, ZoneId zone) {
        return DATE.format(Instant.ofEpochSecond(seconds).atZone(zone));
    }

    /** Month and year, such as "Aug 2026", for the tight columns in Full detail. */
    public static String monthYear(long seconds) {
        return MONTH_YEAR.format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()));
    }

    /** A short form for tight spaces, such as "2mo". */
    public static String compact(long thenSeconds, long nowSeconds) {
        long elapsed = Math.max(0, nowSeconds - thenSeconds);
        if (elapsed < MINUTE) {
            return "now";
        }
        if (elapsed < HOUR) {
            return elapsed / MINUTE + "m";
        }
        if (elapsed < DAY) {
            return elapsed / HOUR + "h";
        }
        if (elapsed < MONTH) {
            return elapsed / DAY + "d";
        }
        if (elapsed < YEAR) {
            return elapsed / MONTH + "mo";
        }
        return elapsed / YEAR + "y";
    }

    private static String plural(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s") + " ago";
    }
}
