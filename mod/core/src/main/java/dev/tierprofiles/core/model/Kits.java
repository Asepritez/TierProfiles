package dev.tierprofiles.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Display names for gamemodes. Kits stay plain strings rather than an enum so a
 * kit a list adds tomorrow still works, just with a generated name.
 */
public final class Kits {
    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("sword", "Sword");
        NAMES.put("crystal", "Crystal");
        NAMES.put("axe", "Axe");
        NAMES.put("mace", "Mace");
        NAMES.put("pot", "Pot");
        NAMES.put("neth_pot", "Neth Pot");
        NAMES.put("smp", "SMP");
        NAMES.put("uhc", "UHC");
        NAMES.put("spear", "Spear");
        NAMES.put("shield", "Shield");
        NAMES.put("dia_smp", "Diamond SMP");
        NAMES.put("dia_crystal", "Diamond Crystal");
        NAMES.put("og_vanilla", "OG Vanilla");
        NAMES.put("minecart", "Minecart");
        NAMES.put("trident", "Trident");
        NAMES.put("bed", "Bed");
        NAMES.put("speed", "Speed");
        NAMES.put("debuff", "Debuff");
        NAMES.put("creeper", "Creeper");
        NAMES.put("early_game", "Early Game");
        NAMES.put("late_game", "Late Game");
        NAMES.put("end_game", "End Game");
        NAMES.put("elytra", "Elytra");
        NAMES.put("bow", "Bow");
    }

    private Kits() {
    }

    public static List<String> known() {
        return new ArrayList<>(NAMES.keySet());
    }

    public static String displayName(String kit) {
        String known = NAMES.get(kit);
        return known != null ? known : titleCase(kit);
    }

    private static String titleCase(String kit) {
        StringBuilder out = new StringBuilder();
        for (String word : kit.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }
}
