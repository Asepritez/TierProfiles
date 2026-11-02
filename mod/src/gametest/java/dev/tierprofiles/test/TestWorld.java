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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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

    /**
     * A gallery player: their real public skin from Mojang's profile lookup, which needs
     * no account, plus netherite armour and a weapon, turned `turn` degrees off the camera.
     */
    static void addDuelist(Minecraft client, UUID uuid, String name, double side, double ahead, float turn, String weapon) {
        GameProfile profile = withSkin(uuid, name);
        ClientPacketListener connection = client.getConnection();
        addTabEntry(connection, new PlayerInfo(profile, false));

        RemotePlayer body = new RemotePlayer(client.level, profile);
        body.setId(FIRST_ENTITY_ID + bodies.size());
        Vec3 look = client.player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        body.setPos(client.player.position().add(look.scale(ahead)).add(right.scale(side)));
        float yaw = client.player.getYRot() + 180 + turn;
        body.setYRot(yaw);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
        body.setItemSlot(EquipmentSlot.HEAD, shiny("netherite_helmet"));
        body.setItemSlot(EquipmentSlot.CHEST, shiny("netherite_chestplate"));
        body.setItemSlot(EquipmentSlot.LEGS, shiny("netherite_leggings"));
        body.setItemSlot(EquipmentSlot.FEET, shiny("netherite_boots"));
        body.setItemSlot(EquipmentSlot.MAINHAND, shiny(weapon));
        body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("totem_of_undying"))));
        client.level.addEntity(body);
        bodies.add(body);
    }

    private static ItemStack shiny(String item) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(item)));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static GameProfile withSkin(UUID uuid, String name) {
        try {
            java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
            String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "") + "?unsigned=false";
            String body = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            com.google.common.collect.ImmutableMultimap.Builder<String, com.mojang.authlib.properties.Property> properties =
                    com.google.common.collect.ImmutableMultimap.builder();
            for (var element : json.getAsJsonArray("properties")) {
                com.google.gson.JsonObject property = element.getAsJsonObject();
                String signature = property.has("signature") ? property.get("signature").getAsString() : null;
                properties.put(property.get("name").getAsString(), new com.mojang.authlib.properties.Property(
                        property.get("name").getAsString(), property.get("value").getAsString(), signature));
            }
            return new GameProfile(uuid, name, new com.mojang.authlib.properties.PropertyMap(properties.build()));
        } catch (Exception e) {
            return new GameProfile(uuid, name);
        }
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
