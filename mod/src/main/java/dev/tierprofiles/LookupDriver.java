package dev.tierprofiles;

import dev.tierprofiles.core.logic.KitDetector;
import dev.tierprofiles.core.net.ProfileService;
import dev.tierprofiles.core.settings.Filters;
import dev.tierprofiles.core.settings.SurfaceSettings;
import dev.tierprofiles.hud.CardState;
import dev.tierprofiles.input.TabSelection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Keeps the lookup queue topped up from the tab list. We only ever ask about
 * players the server has already told every client about, which is why this mod
 * can't reveal anyone hidden.
 */
final class LookupDriver {
    private static final int TICKS_BETWEEN_SCANS = 20;
    private static final int MAX_NAMES_PER_ROW = 3;
    private static final Pattern WORD_BREAK = Pattern.compile("[^A-Za-z0-9_]+");

    private int ticks;

    void tick(Minecraft minecraft) {
        CardState.tick();
        if (minecraft.player != null && !TabSelection.isListVisible()) {
            TabSelection.clear();
        }
        if (++ticks < TICKS_BETWEEN_SCANS) {
            return;
        }
        ticks = 0;

        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return;
        }
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            UUID uuid = info.getProfile().id();
            if (ProfileService.isLookupEligible(uuid)) {
                TierProfiles.profiles().request(uuid, info.getProfile().name());
            } else {
                TierProfiles.profiles().requestByName(uuid, namesShownBy(info));
            }
        }

        detectKit(minecraft.player);
    }

    /**
     * Names a made-up tab row might stand for, like a server's list of top players. Its
     * own profile name always counts. Words in its text only count when the row wears a
     * skin, so info lines such as "Ping: 20ms" are never mistaken for a player.
     */
    private static List<String> namesShownBy(PlayerInfo info) {
        List<String> names = new ArrayList<>();
        names.add(info.getProfile().name());
        Component shown = info.getTabListDisplayName();
        if (shown != null && info.getProfile().properties().containsKey("textures")) {
            for (String word : WORD_BREAK.split(shown.getString())) {
                if (word.chars().anyMatch(Character::isLetter)) {
                    names.add(word);
                }
            }
        }
        return names.size() > MAX_NAMES_PER_ROW ? names.subList(0, MAX_NAMES_PER_ROW) : names;
    }

    private static void detectKit(LocalPlayer player) {
        if (player == null || !anySurfaceUsesAuto()) {
            TierProfiles.setDetectedKit(null);
            return;
        }
        Inventory inventory = player.getInventory();
        Set<String> items = new HashSet<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            items.add(BuiltInRegistries.ITEM.getKey(inventory.getItem(slot).getItem()).toString());
        }
        TierProfiles.setDetectedKit(KitDetector.detect(items));
    }

    private static boolean anySurfaceUsesAuto() {
        for (SurfaceSettings surface : TierProfiles.settings().surfaces.values()) {
            if (surface.kitMode == Filters.KitMode.AUTO) {
                return true;
            }
        }
        return false;
    }
}
