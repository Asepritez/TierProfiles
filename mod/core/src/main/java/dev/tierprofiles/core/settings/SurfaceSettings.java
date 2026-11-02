package dev.tierprofiles.core.settings;

import dev.tierprofiles.core.Maths;
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

    public boolean hideRetired = false;
    public boolean hideUnranked = true;
    public boolean hideOwnTag = false;
    public TierMode tierMode = TierMode.CURRENT;
    public Filters.KitMode kitMode = Filters.KitMode.HIGHEST;
    /** Whether name highlight rules apply on this surface. */
    public boolean highlightName = true;

    /** What to show when a player's current tier and best-ever tier differ. */
    public enum TierMode {
        CURRENT, PEAK, BOTH
    }

    public static SurfaceSettings defaultsFor(Surface surface) {
        SurfaceSettings settings = new SurfaceSettings();
        settings.enabled = surface.enabledByDefault();
        if (surface == Surface.LOOK_AT) {
            settings.maxTags = 3;
        }
        return settings;
    }

    public Tier minimum() {
        return Tier.parse(minimumTier);
    }

    void sanitize() {
        if (Tier.parse(minimumTier) == null) {
            minimumTier = null;
        }
        maxTags = Maths.clamp(maxTags, 1, MAX_TAGS);
        tierMode = tierMode == null ? TierMode.CURRENT : tierMode;
        kitMode = kitMode == null ? Filters.KitMode.HIGHEST : kitMode;
    }
}
