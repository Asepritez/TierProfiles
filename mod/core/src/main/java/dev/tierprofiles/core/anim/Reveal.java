package dev.tierprofiles.core.anim;

/**
 * Something that opens and closes over time, such as a card. Position comes from
 * timestamps and never from counting frames, so the speed doesn't depend on frame
 * rate. Reversing part way carries on from where it is, so nothing ever jumps.
 */
public final class Reveal {
    private boolean open;
    private float from;
    private long startedAt;
    private long duration;

    /** Starts moving towards open or closed. Zero duration means go there at once. */
    public void set(boolean open, long now, long durationMillis) {
        from = position(now);
        this.open = open;
        startedAt = now;
        duration = durationMillis;
    }

    /** Linear position from 0 (closed) to 1 (open). */
    public float position(long now) {
        float target = open ? 1 : 0;
        if (duration <= 0) {
            return target;
        }
        float t = Math.clamp((float) (now - startedAt) / duration, 0, 1);
        return from + (target - from) * t;
    }

    /** Eased position, which is what drawing code should use. */
    public float eased(long now) {
        return Easing.OUT_CUBIC.apply(position(now));
    }

    public boolean isOpen() {
        return open;
    }

    /** False once fully closed, when there's nothing left to draw. */
    public boolean isVisible(long now) {
        return open || position(now) > 0;
    }
}
