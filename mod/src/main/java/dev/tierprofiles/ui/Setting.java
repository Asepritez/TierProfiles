package dev.tierprofiles.ui;

import java.util.Locale;

/**
 * One line in the settings. Every setting in the mod is one of these, so search and
 * the Advanced sections come from the same list and can't disagree.
 *
 * @param tab      which tab it lives on
 * @param group    the heading above it, also shown as its path in search results
 * @param advanced tucked under the tab's Advanced section until you open it
 * @param hint     a sentence shown when you hover
 */
public record Setting(SettingsTab tab, String group, String label, String hint, boolean advanced, Control control) {
    /** Draws the control with its right side at `right`, centred on `centerY`. */
    @FunctionalInterface
    public interface Control {
        void draw(Ui ui, int right, int centerY);
    }

    public String path() {
        return tab.title() + " / " + group;
    }

    public boolean matches(String query) {
        String wanted = query.toLowerCase(Locale.ROOT);
        return label.toLowerCase(Locale.ROOT).contains(wanted)
                || path().toLowerCase(Locale.ROOT).contains(wanted)
                || hint.toLowerCase(Locale.ROOT).contains(wanted);
    }
}
