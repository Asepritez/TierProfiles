package dev.tierprofiles.core;

/**
 * Math.clamp arrived in Java 21, but Minecraft 1.18 to 1.20.4 run on Java 17. Core code
 * uses these instead so the same core works on every version we support.
 */
public final class Maths {
    private Maths() {
    }

    public static int clamp(long value, int min, int max) {
        return (int) Math.min(max, Math.max(min, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.min(max, Math.max(min, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }
}
