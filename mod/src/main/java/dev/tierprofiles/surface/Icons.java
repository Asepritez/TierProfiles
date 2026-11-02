package dev.tierprofiles.surface;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Style;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * Icons are characters. Our two fonts map private-use characters to small images,
 * so an icon can sit inside ordinary text and works the same on a nametag, in the
 * tab list and in chat. The order here must match assets/tierprofiles/font.
 */
public final class Icons {
    private static final FontDescription.Resource CLASSIC = font("icons_classic");
    private static final FontDescription.Resource CURRENT = font("icons_current");
    private static final FontDescription.Resource PIXEL = font("icons_pixel");

    private static final int FIRST_KIT = 0xE100;
    private static final int FIRST_LIST = 0xE200;

    private static final List<String> KITS = List.of(
            "sword", "crystal", "axe", "mace", "pot", "neth_pot", "smp", "uhc", "spear", "shield", "dia_smp",
            "dia_crystal", "og_vanilla", "minecart", "trident", "bed", "speed", "debuff", "creeper", "early_game",
            "late_game", "end_game", "elytra", "bow");

    private Icons() {
    }

    private static FontDescription.Resource font(String name) {
        return new FontDescription.Resource(Identifier.fromNamespaceAndPath(TierProfiles.MOD_ID, name));
    }

    /** Returns an empty component for a kit we have no art for, such as a brand new one. */
    public static MutableComponent kit(String kit, Style.IconSet set) {
        int index = KITS.indexOf(kit);
        return index < 0 ? Component.empty() : glyph(FIRST_KIT + index, fontFor(set));
    }

    public static MutableComponent list(TierList list) {
        return glyph(FIRST_LIST + list.ordinal(), CLASSIC);
    }

    private static FontDescription fontFor(Style.IconSet set) {
        return switch (set) {
            case DEFAULT -> CURRENT;
            case CLASSIC -> CLASSIC;
            case PIXEL -> PIXEL;
        };
    }

    private static MutableComponent glyph(int codePoint, FontDescription font) {
        return Component.literal(Character.toString(codePoint)).withStyle(style -> style.withFont(font).withColor(0xFFFFFF));
    }
}
