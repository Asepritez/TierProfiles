package dev.tierprofiles.core.logic;

import dev.tierprofiles.core.settings.HighlightRule;

/** How a name should look. `color` null means leave the game's colour alone. */
public record NameStyle(Integer color, Integer gradientEnd, boolean bold, boolean glow, boolean star) {
    public static final NameStyle NONE = new NameStyle(null, null, false, false, false);

    static NameStyle of(HighlightRule rule) {
        return new NameStyle(rule.color, rule.gradientEnd, rule.bold, rule.glow, rule.star);
    }

    public boolean isGradient() {
        return color != null && gradientEnd != null;
    }

    public boolean isPlain() {
        return equals(NONE);
    }
}
