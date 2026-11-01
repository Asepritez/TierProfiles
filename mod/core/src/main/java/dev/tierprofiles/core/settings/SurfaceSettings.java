package dev.tierprofiles.core.settings;

import dev.tierprofiles.core.model.Tier;

public final class SurfaceSettings {
    public static final int MAX_TAGS = 5;

    public boolean enabled = true;
    /** Hide tags below this tier. Null shows everything. */
    public String minimumTier = null;
    /** How many lists may show a tag at once. */
    public int maxTags = 1;
    public boolean kitIcon = true;
    public boolean listLogo = false;
    public boolean region = false;

    public static SurfaceSettings defaultsFor(Surface surface) {
        SurfaceSettings settings = new SurfaceSettings();
        settings.enabled = surface.enabledByDefault();
        return settings;
    }

    public Tier minimum() {
        return Tier.parse(minimumTier);
    }

    void sanitize() {
        if (Tier.parse(minimumTier) == null) {
            minimumTier = null;
        }
        maxTags = Math.clamp(maxTags, 1, MAX_TAGS);
    }
}
