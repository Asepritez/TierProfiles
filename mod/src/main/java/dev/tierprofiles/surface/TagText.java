package dev.tierprofiles.surface;

import dev.tierprofiles.core.logic.Gradient;
import dev.tierprofiles.core.logic.NameStyle;
import dev.tierprofiles.core.logic.Segment;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Style;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;

/** Turns the core's plain descriptions into real Minecraft text. */
public final class TagText {
    private static final int STAR_COLOR = 0xFFCF4A;

    private TagText() {
    }

    public static MutableComponent build(List<Segment> segments, Style style) {
        MutableComponent out = Component.empty();
        for (Segment segment : segments) {
            switch (segment.kind()) {
                case TEXT -> out.append(Component.literal(segment.text()).withColor(segment.rgb()));
                case KIT_ICON -> out.append(Icons.kit(segment.text(), style.iconSet));
                case LIST_ICON -> {
                    TierList list = TierList.byId(segment.text());
                    if (list != null) {
                        out.append(Icons.list(list));
                    }
                }
            }
        }
        return out;
    }

    public static Component styleName(Component name, NameStyle style) {
        if (style.isPlain()) {
            return name;
        }

        MutableComponent out;
        if (style.isGradient()) {
            out = gradient(name.getString(), style.color(), style.gradientEnd());
        } else if (style.color() != null) {
            out = Component.literal(name.getString()).withColor(style.color());
        } else {
            out = name.copy();
        }

        if (style.bold()) {
            out.withStyle(text -> text.withBold(true));
        }
        if (style.glow()) {
            applyGlow(out, style);
        }
        if (style.star()) {
            return Component.empty().append(Component.literal("★ ").withColor(STAR_COLOR)).append(out);
        }
        return out;
    }

    /** The glow is a coloured text shadow, which reads as a soft halo on dark backgrounds. */
    public static void applyGlow(MutableComponent text, NameStyle style) {
        int color = style.color() != null ? style.color() : 0xFFFFFF;
        text.withStyle(current -> current.withShadowColor(ARGB.opaque(color)));
    }

    private static MutableComponent gradient(String text, int from, int to) {
        int[] codePoints = text.codePoints().toArray();
        int[] colors = Gradient.across(codePoints.length, from, to);
        MutableComponent out = Component.empty();
        for (int i = 0; i < codePoints.length; i++) {
            out.append(Component.literal(Character.toString(codePoints[i])).withColor(colors[i]));
        }
        return out;
    }
}
