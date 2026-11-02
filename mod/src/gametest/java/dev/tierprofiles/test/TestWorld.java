package dev.tierprofiles.test;

import com.mojang.authlib.GameProfile;
import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.config.ConfigStore;
import dev.tierprofiles.core.settings.ConfigFile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Puts real tier players into a singleplayer test world. The dev account has an
 * offline UUID, which the lookup rightly skips, and a lone player gets no tab list.
 * So this adds tab entries and client-side bodies using real public UUIDs, and the
 * lookups then go to the real local server. This code never ships in the mod.
 */
final class TestWorld {
    static final UUID JOKING = UUID.fromString("1246a0ec-662b-45de-bb0b-d68cb1cce800");
    static final UUID ITZ = UUID.fromString("06ec3577-3299-45fa-bbdf-613b1f86c8ab");
    static final UUID NOBODY = UUID.fromString("3f1c9a62-7d41-4e0b-9a55-1b2c7e8f0a11");

    private static final int FIRST_ENTITY_ID = 900_000;
    private static final List<RemotePlayer> bodies = new ArrayList<>();

    private TestWorld() {
    }

    static void acceptNotice() {
        ConfigFile config = ConfigStore.copy(TierProfiles.config());
        config.global.noticeSeen = true;
        // The tests save a card, which needs the local server and its stand-in Mojang.
        config.global.source = dev.tierprofiles.core.settings.Global.Source.SERVER;
        // The test server on this PC, never the live one.
        config.global.serverUrl = dev.tierprofiles.core.settings.Global.LOCAL_SERVER_URL;
        TierProfiles.publish(config);
    }

    /** Stands a player in front of the camera, `side` blocks to the right. */
    static void addPlayer(Minecraft client, UUID uuid, String name, double side, double ahead) {
        GameProfile profile = new GameProfile(uuid, name);
        ClientPacketListener connection = client.getConnection();
        addTabEntry(connection, new PlayerInfo(profile, false));

        RemotePlayer body = new RemotePlayer(client.level, profile);
        body.setId(FIRST_ENTITY_ID + bodies.size());
        Vec3 look = client.player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        Vec3 spot = client.player.position().add(look.scale(ahead)).add(right.scale(side));
        body.setPos(spot);
        body.setYRot(client.player.getYRot() + 180);
        body.setYHeadRot(client.player.getYRot() + 180);
        body.setYBodyRot(client.player.getYRot() + 180);
        client.level.addEntity(body);
        bodies.add(body);
    }

    @SuppressWarnings("unchecked")
    private static void addTabEntry(ClientPacketListener connection, PlayerInfo info) {
        try {
            Field map = ClientPacketListener.class.getDeclaredField("playerInfoMap");
            Field listed = ClientPacketListener.class.getDeclaredField("listedPlayers");
            map.setAccessible(true);
            listed.setAccessible(true);
            ((Map<UUID, PlayerInfo>) map.get(connection)).put(info.getProfile().id(), info);
            ((Set<PlayerInfo>) listed.get(connection)).add(info);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
