package dev.tierprofiles.core.anim;

import dev.tierprofiles.core.Maths;
/**
 * Something that opens and closes over time, such as a card. Its position comes from
 * timestamps and never from counting frames, so the speed doesn't depend on frame
 * rate. Each move starts from wherever it currently is, so reversing part way can't jump.
 */
public final class Reveal {
    private boolean open;
    private float from;
    private long startedAt;
    private long duration;
    private Easing easing = Easing.LINEAR;

    public void set(boolean open, long now, long durationMillis) {
        set(open, now, durationMillis, Easing.LINEAR);
    }

    /** Starts moving towards open or closed. Zero duration means go there at once. */
    public void set(boolean open, long now, long durationMillis, Easing curve) {
        from = position(now);
        this.open = open;
        startedAt = now;
        duration = durationMillis;
        easing = curve;
    }

    /** Where it is now, from 0 (closed) to 1 (open). */
    public float position(long now) {
        float target = open ? 1 : 0;
        if (duration <= 0) {
            return target;
        }
        float t = Maths.clamp((float) (now - startedAt) / duration, 0, 1);
        return from + (target - from) * easing.apply(t);
    }

    public boolean isOpen() {
        return open;
    }

    /** False once fully closed, when there's nothing left to draw. */
    public boolean isVisible(long now) {
        return open || position(now) > 0;
    }
}
