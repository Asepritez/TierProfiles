package dev.tierprofiles.core.settings;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a named profile holds, and everything a share code can carry. It must
 * never hold anything personal or anything that changes where data is sent. Those
 * belong in Global, which share codes can't reach.
 */
public final class Settings {
    public static final int MAX_RULES = 32;
    public static final int MAX_ICON = 8;

    public Filters filters = new Filters();
    public Map<Surface, SurfaceSettings> surfaces = defaultSurfaces();
    public Style style = new Style();
    public List<HighlightRule> rules = new ArrayList<>();
    public CardSettings card = new CardSettings();
    /** Which icon this setup has in the hotbar. */
    public int icon = 0;

    private static Map<Surface, SurfaceSettings> defaultSurfaces() {
        Map<Surface, SurfaceSettings> surfaces = new EnumMap<>(Surface.class);
        for (Surface surface : Surface.values()) {
            surfaces.put(surface, SurfaceSettings.defaultsFor(surface));
        }
        return surfaces;
    }

    public SurfaceSettings surface(Surface surface) {
        return surfaces.get(surface);
    }

    /**
     * Repairs whatever came from a file or a friend's share code: fills in gaps,
     * clamps numbers and drops anything invalid. After this, the rest of the mod
     * can trust every field.
     */
    public Settings sanitize() {
        filters = filters == null ? new Filters() : filters;
        filters.sanitize();

        Map<Surface, SurfaceSettings> clean = defaultSurfaces();
        if (surfaces != null) {
            for (Map.Entry<Surface, SurfaceSettings> entry : surfaces.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    entry.getValue().sanitize();
                    clean.put(entry.getKey(), entry.getValue());
                }
            }
        }
        surfaces = clean;

        style = style == null ? new Style() : style;
        style.sanitize();

        card = card == null ? new CardSettings() : card;
        card.sanitize();

        List<HighlightRule> cleanRules = new ArrayList<>();
        if (rules != null) {
            for (HighlightRule rule : rules) {
                if (rule != null && cleanRules.size() < MAX_RULES) {
                    rule.sanitize();
                    cleanRules.add(rule);
                }
            }
        }
        rules = cleanRules;
        icon = Math.clamp(icon, 0, MAX_ICON);
        return this;
    }
}
