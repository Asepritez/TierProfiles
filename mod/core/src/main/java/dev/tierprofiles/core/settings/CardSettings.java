package dev.tierprofiles.core.settings;

/** The look-at card and the full profile card. */
public final class CardSettings {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;
    public static final int MAX_CLOSE_SECONDS = 120;
    public static final int MAX_DELAY_MS = 2000;

    public float lookAtScale = 1.0f;
    public float lookAtOpacity = 0.9f;
    public int lookAtDelayMs = 150;
    public LookAtAnchor lookAtAnchor = LookAtAnchor.BELOW_CROSSHAIR;

    public float overlayScale = 1.0f;
    public float overlayOpacity = 0.92f;
    /** Zero keeps the card open until it's closed by hand. */
    public int overlayCloseSeconds = 15;
    public OverlayAnchor overlayAnchor = OverlayAnchor.RIGHT;

    public boolean showModel = true;
    public boolean spinModel = true;
    public boolean showPeak = true;
    public boolean showAttained = true;
    public boolean showRetired = true;
    public boolean showUnavailableLists = true;
    public boolean showPoints = true;
    public boolean showCountry = true;

    public enum LookAtAnchor {
        BELOW_CROSSHAIR, TOP, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public enum OverlayAnchor {
        LEFT, CENTER, RIGHT
    }

    void sanitize() {
        lookAtScale = clampScale(lookAtScale);
        overlayScale = clampScale(overlayScale);
        lookAtOpacity = clampOpacity(lookAtOpacity);
        overlayOpacity = clampOpacity(overlayOpacity);
        lookAtDelayMs = Math.clamp(lookAtDelayMs, 0, MAX_DELAY_MS);
        overlayCloseSeconds = Math.clamp(overlayCloseSeconds, 0, MAX_CLOSE_SECONDS);
        lookAtAnchor = lookAtAnchor == null ? LookAtAnchor.BELOW_CROSSHAIR : lookAtAnchor;
        overlayAnchor = overlayAnchor == null ? OverlayAnchor.RIGHT : overlayAnchor;
    }

    private static float clampScale(float value) {
        return Float.isFinite(value) ? Math.clamp(value, MIN_SCALE, MAX_SCALE) : 1.0f;
    }

    private static float clampOpacity(float value) {
        return Float.isFinite(value) ? Math.clamp(value, 0.0f, 1.0f) : 0.9f;
    }
}
