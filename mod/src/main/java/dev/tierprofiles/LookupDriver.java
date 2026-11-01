package dev.tierprofiles;

import dev.tierprofiles.core.logic.KitDetector;
import dev.tierprofiles.core.settings.Filters;
import dev.tierprofiles.core.settings.SurfaceSettings;
import dev.tierprofiles.hud.CardState;
import dev.tierprofiles.input.TabSelection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;

/**
 * Keeps the lookup queue topped up from the tab list. We only ever ask about
 * players the server has already told every client about, which is why this mod
 * can't reveal anyone hidden.
 */
final class LookupDriver {
    private static final int TICKS_BETWEEN_SCANS = 20;

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
            TierProfiles.profiles().request(info.getProfile().id(), info.getProfile().name());
        }

        detectKit(minecraft.player);
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
