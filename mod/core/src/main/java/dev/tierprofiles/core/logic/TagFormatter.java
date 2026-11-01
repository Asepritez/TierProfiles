package dev.tierprofiles.core.logic;

import dev.tierprofiles.core.settings.Colors;
import dev.tierprofiles.core.settings.Style;
import dev.tierprofiles.core.settings.SurfaceSettings;
import java.util.ArrayList;
import java.util.List;

/** Turns picked tags into segments, following the tag format settings. */
public final class TagFormatter {
    public static final int PUNCTUATION = 0xAAAAAA;
    public static final int UNRANKED = 0x777777;
    private static final float RETIRED_DIM = 0.6f;

    private TagFormatter() {
    }

    public static List<Segment> format(List<Tag> tags, SurfaceSettings surface, Style style) {
        List<Segment> out = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                out.add(Segment.text(style.separator.text, PUNCTUATION));
            }
            appendTag(out, tags.get(i), surface, style);
        }
        return out;
    }

    private static void appendTag(List<Segment> out, Tag tag, SurfaceSettings surface, Style style) {
        if (!style.brackets.open.isEmpty()) {
            out.add(Segment.text(style.brackets.open, PUNCTUATION));
        }

        if (tag.isUnranked()) {
            out.add(Segment.text(style.longNames ? "Unranked" : "-", UNRANKED));
        } else {
            if (surface.listLogo) {
                out.add(new Segment(Segment.Kind.LIST_ICON, tag.list().id(), 0xFFFFFF));
            }
            if (surface.kitIcon) {
                out.add(new Segment(Segment.Kind.KIT_ICON, tag.kit(), 0xFFFFFF));
            }

            int color = style.color(tag.list(), tag.tier());
            String label = style.longNames ? tag.tier().longLabel() : tag.tier().label();
            if (tag.retired()) {
                if (style.retiredMark == Style.RetiredMark.DIM) {
                    color = Colors.scale(color, RETIRED_DIM);
                } else if (style.retiredMark == Style.RetiredMark.SUFFIX) {
                    label += "R";
                }
            }
            out.add(Segment.text(label, color));

            if (tag.higherPeak() != null) {
                out.add(Segment.text(" (", PUNCTUATION));
                out.add(Segment.text(tag.higherPeak().label(), style.color(tag.list(), tag.higherPeak())));
                out.add(Segment.text(")", PUNCTUATION));
            }

            if (surface.region && tag.region() != null) {
                out.add(Segment.text(" " + tag.region(), PUNCTUATION));
            }
        }

        if (!style.brackets.close.isEmpty()) {
            out.add(Segment.text(style.brackets.close, PUNCTUATION));
        }
    }

    /** The text alone, with icons left out. Handy for tests and previews. */
    public static String plainText(List<Segment> segments) {
        StringBuilder out = new StringBuilder();
        for (Segment segment : segments) {
            if (segment.kind() == Segment.Kind.TEXT) {
                out.append(segment.text());
            }
        }
        return out.toString();
    }
}
