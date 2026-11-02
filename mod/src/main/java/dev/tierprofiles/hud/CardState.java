package dev.tierprofiles.hud;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.anim.Easing;
import dev.tierprofiles.core.anim.Reveal;
import dev.tierprofiles.core.settings.CardSettings;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Which player's card is pinned, if any. Used only from the game's main thread. */
public final class CardState {
    private static final long OPEN_MILLIS = 500;
    private static final long CLOSE_MILLIS = 300;

    /** Replaceable so tests can freeze an animation at an exact moment. */
    private static LongSupplier clock = System::currentTimeMillis;

    private static final Reveal reveal = new Reveal();
    private static UUID uuid;
    private static String name;
    private static long openedAt;
    private static long contentSince;
    private static boolean allRanks;
    /** Shown because it's selected in Tab, not pinned. It goes when Tab is let go. */
    private static boolean preview;

    private CardState() {
    }

    public static long now() {
        return clock.getAsLong();
    }

    public static void setClock(LongSupplier replacement) {
        clock = replacement;
    }

    public static void useRealClock() {
        clock = System::currentTimeMillis;
    }

    private static long duration(long millis) {
        CardSettings card = TierProfiles.settings().card;
        return card.effectiveStyle(TierProfiles.config().global.reduceMotion) == CardSettings.AnimationStyle.OFF
                ? 0
                : card.scaled(millis);
    }

    public static void open(UUID player, String playerName) {
        open(player, playerName, false);
    }

    /** @param everyRank open straight into All ranks, with every kit showing */
    public static void open(UUID player, String playerName, boolean everyRank) {
        preview = false;
        uuid = player;
        name = playerName;
        openedAt = now();
        contentSince = openedAt;
        allRanks = everyRank;
        reveal.set(true, openedAt, duration(OPEN_MILLIS), Easing.OUT_QUART);
    }

    /**
     * Switches the open card between its usual detail and All ranks. The rows glide in
     * again so the change reads as one motion, and the close timer starts over.
     */
    public static void toggleAllRanks() {
        allRanks = !allRanks;
        openedAt = now();
        contentSince = openedAt;
    }

    /**
     * Shows the selected player's card while you browse Tab. Moving to the next player swaps
     * the card without replaying the whole entrance, so browsing stays quick.
     */
    public static void preview(UUID player, String playerName) {
        boolean showing = isOpen();
        if (showing && player.equals(uuid)) {
            return;
        }
        if (showing) {
            uuid = player;
            name = playerName;
            contentSince = now();
            openedAt = contentSince;
            allRanks = false;
        } else {
            open(player, playerName, false);
        }
        preview = true;
    }

    public static boolean isPreview() {
        return preview && isOpen();
    }

    /** Keeps the previewed card, as if it had been opened with Enter. */
    public static void pin() {
        preview = false;
        openedAt = now();
    }

    public static boolean allRanks() {
        return allRanks;
    }

    /** Starts the card leaving. It keeps drawing until it has gone. */
    public static void close() {
        reveal.set(false, now(), duration(CLOSE_MILLIS), Easing.IN_CUBIC);
    }

    /** Open means staying up. A card on its way out no longer counts. */
    public static boolean isOpen() {
        return uuid != null && reveal.isOpen();
    }

    public static boolean isVisible() {
        return uuid != null && reveal.isVisible(now());
    }

    public static boolean isShowing(UUID player) {
        return isOpen() && player.equals(uuid);
    }

    public static UUID uuid() {
        return uuid;
    }

    public static String name() {
        return name;
    }

    /** How far open, from 0 to 1. */
    public static float progress() {
        return reveal.position(now());
    }

    public static long millisSinceOpen() {
        return now() - openedAt;
    }

    /** Time since the rows last started arriving, for their stagger. */
    public static long millisSinceContent() {
        return now() - contentSince;
    }

    /** How much of the open time is left, from 1 down to 0. Always 1 with no time limit. */
    public static float remaining() {
        int seconds = TierProfiles.settings().card.overlayCloseSeconds;
        if (seconds == 0 || preview) {
            return 1;
        }
        float elapsed = millisSinceOpen() / 1000f;
        return 1 - Math.clamp(elapsed / seconds, 0, 1);
    }

    public static void tick() {
        if (isOpen() && remaining() <= 0) {
            close();
        }
    }
}
