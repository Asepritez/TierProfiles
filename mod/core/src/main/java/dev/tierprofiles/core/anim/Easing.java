package dev.tierprofiles.core.anim;

/** Curves that turn even progress from 0 to 1 into natural-looking movement. */
public enum Easing {
    LINEAR {
        @Override
        public float apply(float t) {
            return t;
        }
    },
    /** Starts fast and settles gently, like something sliding to a stop. */
    OUT_CUBIC {
        @Override
        public float apply(float t) {
            float rest = 1 - t;
            return 1 - rest * rest * rest;
        }
    },
    /** Overshoots slightly before settling, for a small pop. */
    OUT_BACK {
        @Override
        public float apply(float t) {
            float rest = t - 1;
            return 1 + 2.7f * rest * rest * rest + 1.7f * rest * rest;
        }
    };

    public abstract float apply(float t);
}
