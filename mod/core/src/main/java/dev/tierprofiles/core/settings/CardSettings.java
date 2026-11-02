package dev.tierprofiles.core.settings;

/** The look-at card and the full profile card. */
public final class CardSettings {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;
    public static final int MAX_CLOSE_SECONDS = 120;
    public static final int MAX_DELAY_MS = 2000;
    public static final int MAX_OFFSET = 2000;
    public static final float MIN_SPEED = 0.25f;
    public static final float MAX_SPEED = 3.0f;

    public float lookAtScale = 1.0f;
    public float lookAtOpacity = 0.9f;
    public int lookAtDelayMs = 150;
    public LookAtAnchor lookAtAnchor = LookAtAnchor.BELOW_CROSSHAIR;

    public float overlayScale = 1.0f;
    public float overlayOpacity = 0.92f;
    /** Zero keeps the card open until it's closed by hand. */
    public int overlayCloseSeconds = 15;
    public Anchor anchor = Anchor.TOP_RIGHT;
    public int offsetX = 0;
    public int offsetY = 0;
    /** Dragging the card pulls it to anchors and edges. */
    public boolean snap = true;

    public boolean showModel = true;
    public boolean spinModel = true;
    public boolean showPeak = true;
    public boolean showAttained = true;
    public boolean showRetired = true;
    public boolean showPoints = true;
    public boolean showCountry = true;

    public Detail detail = Detail.STANDARD;
    public AnimationStyle animationStyle = AnimationStyle.GLIDE;
    /** 1 is normal, 2 is twice as fast. */
    public float animationSpeed = 1.0f;

    /** How much each list's row says. */
    public enum Detail {
        /** Tiers only, with unranked lists left out. */
        MINIMAL,
        /** Your best few tiers with the date each was achieved. */
        STANDARD,
        /** Every tier, with dates. */
        FULL
    }

    public enum AnimationStyle {
        GLIDE, FADE, SCALE, OFF
    }

    /** Reduce motion wins over everything. */
    public AnimationStyle effectiveStyle(boolean reduceMotion) {
        return reduceMotion ? AnimationStyle.OFF : animationStyle;
    }

    /** A duration adjusted for the speed setting. */
    public long scaled(long millis) {
        return Math.round(millis / (double) animationSpeed);
    }

    public enum LookAtAnchor {
        BELOW_CROSSHAIR, TOP, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    /** Where the card is pinned. Offsets then nudge it from there. */
    public enum Anchor {
        TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
        MIDDLE_LEFT(0, 1), CENTER(1, 1), MIDDLE_RIGHT(2, 1),
        BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2);

        private final int column;
        private final int row;

        Anchor(int column, int row) {
            this.column = column;
            this.row = row;
        }

        public int column() {
            return column;
        }

        public int row() {
            return row;
        }
    }

    void sanitize() {
        lookAtScale = clampScale(lookAtScale);
        overlayScale = clampScale(overlayScale);
        lookAtOpacity = clampOpacity(lookAtOpacity);
        overlayOpacity = clampOpacity(overlayOpacity);
        lookAtDelayMs = Math.clamp(lookAtDelayMs, 0, MAX_DELAY_MS);
        overlayCloseSeconds = Math.clamp(overlayCloseSeconds, 0, MAX_CLOSE_SECONDS);
        lookAtAnchor = lookAtAnchor == null ? LookAtAnchor.BELOW_CROSSHAIR : lookAtAnchor;
        anchor = anchor == null ? Anchor.TOP_RIGHT : anchor;
        offsetX = Math.clamp(offsetX, -MAX_OFFSET, MAX_OFFSET);
        offsetY = Math.clamp(offsetY, -MAX_OFFSET, MAX_OFFSET);
        detail = detail == null ? Detail.STANDARD : detail;
        animationStyle = animationStyle == null ? AnimationStyle.GLIDE : animationStyle;
        animationSpeed = Float.isFinite(animationSpeed) ? Math.clamp(animationSpeed, MIN_SPEED, MAX_SPEED) : 1.0f;
    }

    private static float clampScale(float value) {
        return Float.isFinite(value) ? Math.clamp(value, MIN_SCALE, MAX_SCALE) : 1.0f;
    }

    private static float clampOpacity(float value) {
        return Float.isFinite(value) ? Math.clamp(value, 0.0f, 1.0f) : 0.9f;
    }
}
