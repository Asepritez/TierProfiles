package dev.tierprofiles.ui;

import java.util.Locale;

/**
 * One line in the settings. Every setting in the mod is one of these, so search and the
 * Advanced sections come from the same list and can't disagree.
 *
 * @param tab      which tab it lives on
 * @param path     where it lives, shown in search results
 * @param surface  for per-surface settings, which one, or else null
 * @param advanced tucked under the tab's Advanced section until you open it
 * @param hint     a sentence shown when you hover
 */
public record Setting(SettingsTab tab, String path, Object surface, String label, String hint, boolean advanced, Control control) {
    /** Draws the control starting at `x`, centred on `centerY`, within `width`. */
    @FunctionalInterface
    public interface Control {
        void draw(Ui ui, int x, int centerY, int width);
    }

    public boolean matches(String query) {
        String wanted = query.toLowerCase(Locale.ROOT);
        return label.toLowerCase(Locale.ROOT).contains(wanted)
                || path.toLowerCase(Locale.ROOT).contains(wanted)
                || hint.toLowerCase(Locale.ROOT).contains(wanted);
    }
}
