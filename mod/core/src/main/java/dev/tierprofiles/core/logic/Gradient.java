package dev.tierprofiles.core.logic;

import dev.tierprofiles.core.settings.Colors;

public final class Gradient {
    private Gradient() {
    }

    /** The colour for each character of a text fading from `from` to `to`. */
    public static int[] across(int length, int from, int to) {
        int[] colors = new int[length];
        for (int i = 0; i < length; i++) {
            float amount = length == 1 ? 0 : (float) i / (length - 1);
            colors[i] = Colors.mix(from, to, amount);
        }
        return colors;
    }
}
