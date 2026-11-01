package dev.tierprofiles.core.settings;

import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Style {
    private static final int MAX_COLOR_ENTRIES = 40;

    public Palette palette = Palette.DEFAULT;
    /** Overrides on top of the palette, keyed by tier label such as "HT3". */
    public Map<String, Integer> tierColors = new LinkedHashMap<>();
    public boolean perListColors = false;
    /** Only used when perListColors is on. */
    public Map<TierList, Map<String, Integer>> listColors = new EnumMap<>(TierList.class);

    public Position position = Position.AFTER;
    public Brackets brackets = Brackets.SQUARE;
    public Separator separator = Separator.SPACE;
    public boolean longNames = false;
    public IconSet iconSet = IconSet.TIERPROFILES;
    public RetiredMark retiredMark = RetiredMark.DIM;

    public enum Position {
        BEFORE, AFTER
    }

    public enum Brackets {
        NONE("", ""), SQUARE("[", "]"), ROUND("(", ")"), ANGLE("<", ">"), CURLY("{", "}"), BARS("|", "|");

        public final String open;
        public final String close;

        Brackets(String open, String close) {
            this.open = open;
            this.close = close;
        }
    }

    public enum Separator {
        SPACE(" "), PIPE(" | "), SLASH(" / "), DOT(" • "), DASH(" - ");

        public final String text;

        Separator(String text) {
            this.text = text;
        }
    }

    public enum IconSet {
        /** Our own 16x16 art. */
        TIERPROFILES,
        /** Vanilla items drawn at runtime from the player's own game. */
        VANILLA
    }

    /** How a retired tier looks: faded, marked with an R, or no different. */
    public enum RetiredMark {
        DIM, SUFFIX, NONE
    }

    /** The colour for a tier, with the most specific setting winning. */
    public int color(TierList list, Tier tier) {
        if (perListColors) {
            Map<String, Integer> forList = listColors.get(list);
            Integer color = forList == null ? null : forList.get(tier.label());
            if (color != null) {
                return color;
            }
        }
        Integer shared = tierColors.get(tier.label());
        return shared != null ? shared : palette.color(tier);
    }

    void sanitize() {
        palette = palette == null ? Palette.DEFAULT : palette;
        position = position == null ? Position.AFTER : position;
        brackets = brackets == null ? Brackets.SQUARE : brackets;
        separator = separator == null ? Separator.SPACE : separator;
        iconSet = iconSet == null ? IconSet.TIERPROFILES : iconSet;
        retiredMark = retiredMark == null ? RetiredMark.DIM : retiredMark;

        tierColors = cleanColors(tierColors);

        Map<TierList, Map<String, Integer>> cleanLists = new EnumMap<>(TierList.class);
        if (listColors != null) {
            for (Map.Entry<TierList, Map<String, Integer>> entry : listColors.entrySet()) {
                if (entry.getKey() != null) {
                    cleanLists.put(entry.getKey(), cleanColors(entry.getValue()));
                }
            }
        }
        listColors = cleanLists;
    }

    private static Map<String, Integer> cleanColors(Map<String, Integer> colors) {
        Map<String, Integer> clean = new LinkedHashMap<>();
        if (colors != null) {
            for (Map.Entry<String, Integer> entry : colors.entrySet()) {
                if (Tier.parse(entry.getKey()) != null && entry.getValue() != null && clean.size() < MAX_COLOR_ENTRIES) {
                    clean.put(entry.getKey(), entry.getValue() & 0xFFFFFF);
                }
            }
        }
        return clean;
    }
}
