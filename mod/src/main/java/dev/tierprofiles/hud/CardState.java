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
        uuid = player;
        name = playerName;
        openedAt = now();
        reveal.set(true, openedAt, duration(OPEN_MILLIS), Easing.OUT_QUART);
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

    /** How much of the open time is left, from 1 down to 0. Always 1 with no time limit. */
    public static float remaining() {
        int seconds = TierProfiles.settings().card.overlayCloseSeconds;
        if (seconds == 0) {
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
