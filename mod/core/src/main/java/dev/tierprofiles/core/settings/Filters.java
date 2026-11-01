package dev.tierprofiles.core.settings;

import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierList;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Filters {
    private static final int MAX_SELECTED_KITS = 64;

    public boolean hideRetired = false;
    public boolean hideUnranked = true;
    public boolean hideOwnTag = false;
    public boolean showPeak = false;
    public KitMode kitMode = KitMode.HIGHEST;
    /** Kits that count in SELECTED mode. Earlier kits win ties. */
    public List<String> selectedKits = new ArrayList<>();
    /** Which list wins when there's room for only one. First is strongest. */
    public List<TierList> listPriority = new ArrayList<>(TierList.DEFAULT_PRIORITY);
    public Set<TierList> disabledLists = EnumSet.noneOf(TierList.class);
    /** Per-list minimum, as a tier label. A list with no entry has no minimum. */
    public Map<TierList, String> listMinimums = new EnumMap<>(TierList.class);

    public enum KitMode {
        /** Each list shows the player's best tier in any kit. */
        HIGHEST,
        /** Only the kits you picked count. */
        SELECTED,
        /** Show the kit matching your inventory, or act like HIGHEST if unclear. */
        AUTO
    }

    public boolean isEnabled(TierList list) {
        return !disabledLists.contains(list);
    }

    public Tier listMinimum(TierList list) {
        return Tier.parse(listMinimums.get(list));
    }

    void sanitize() {
        if (kitMode == null) {
            kitMode = KitMode.HIGHEST;
        }

        Set<String> kits = new LinkedHashSet<>();
        if (selectedKits != null) {
            for (String kit : selectedKits) {
                if (kit != null && !kit.isBlank() && kits.size() < MAX_SELECTED_KITS) {
                    kits.add(kit);
                }
            }
        }
        selectedKits = new ArrayList<>(kits);

        // Keep the user's order, drop junk and repeats, and add any list they've
        // never seen, such as one added in a later version.
        Set<TierList> order = new LinkedHashSet<>();
        if (listPriority != null) {
            for (TierList list : listPriority) {
                if (list != null) {
                    order.add(list);
                }
            }
        }
        order.addAll(TierList.DEFAULT_PRIORITY);
        listPriority = new ArrayList<>(order);

        Set<TierList> disabled = EnumSet.noneOf(TierList.class);
        if (disabledLists != null) {
            for (TierList list : disabledLists) {
                if (list != null) {
                    disabled.add(list);
                }
            }
        }
        disabledLists = disabled;

        Map<TierList, String> minimums = new EnumMap<>(TierList.class);
        if (listMinimums != null) {
            for (Map.Entry<TierList, String> entry : listMinimums.entrySet()) {
                if (entry.getKey() != null && Tier.parse(entry.getValue()) != null) {
                    minimums.put(entry.getKey(), entry.getValue());
                }
            }
        }
        listMinimums = minimums;
    }
}
