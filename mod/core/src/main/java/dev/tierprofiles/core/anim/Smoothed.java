package dev.tierprofiles.core.anim;

/**
 * A number that glides towards its target, such as the tab list highlight. Each
 * half-life closes half of the remaining gap. Because that sum uses elapsed time,
 * 60 and 240 frames a second arrive at the same place.
 */
public final class Smoothed {
    private final float halfLifeMillis;
    private float value;
    private long lastUpdate;
    private boolean started;

    public Smoothed(float halfLifeMillis) {
        this.halfLifeMillis = halfLifeMillis;
    }

    public float update(float target, long now) {
        if (!started) {
            snap(target, now);
            return value;
        }
        float elapsed = Math.max(0, now - lastUpdate);
        lastUpdate = now;
        float remaining = (float) Math.pow(0.5, elapsed / halfLifeMillis);
        value = target + (value - target) * remaining;
        return value;
    }

    /** Jumps straight to a value, for the first frame or with reduce motion on. */
    public void snap(float target, long now) {
        value = target;
        lastUpdate = now;
        started = true;
    }

    public void reset() {
        started = false;
    }
}
