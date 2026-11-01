package dev.tierprofiles.core.anim;

/** Items that animate in one after another, each starting a little later. */
public final class Stagger {
    private Stagger() {
    }

    /**
     * @param elapsed  time since the group began
     * @param delay    how long this item waits before it starts
     * @param duration how long this item takes
     */
    public static float progress(long elapsed, long delay, long duration) {
        if (duration <= 0) {
            return elapsed >= delay ? 1 : 0;
        }
        return Easing.OUT_CUBIC.apply(Math.clamp((float) (elapsed - delay) / duration, 0, 1));
    }
}
