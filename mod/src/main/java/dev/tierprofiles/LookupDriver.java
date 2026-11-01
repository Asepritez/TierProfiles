package dev.tierprofiles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Keeps the lookup queue topped up from the tab list. We only ever ask about
 * players the server has already told every client about, which is why this mod
 * can't reveal anyone hidden.
 */
final class LookupDriver {
    private static final int TICKS_BETWEEN_SCANS = 20;

    private int ticks;

    void tick(Minecraft minecraft) {
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
    }
}
