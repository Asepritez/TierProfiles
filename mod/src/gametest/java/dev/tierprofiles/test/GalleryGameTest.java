package dev.tierprofiles.test;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.config.ConfigStore;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.hud.CardState;
import dev.tierprofiles.input.Keys;
import dev.tierprofiles.ui.SettingsScreen;
import dev.tierprofiles.ui.SettingsTab;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Draft gallery shots in a built arena rather than the bare test world. Only runs with
 * `-Pgallery`, which also starts the game as Hoseinplays so your own card has your skin.
 * The players are real, with their public skins and their real tiers.
 */
final class GalleryGameTest {
    static final boolean ON = Boolean.getBoolean("tierprofiles.gallery");

    static final UUID SWIGHT = UUID.fromString("ebd7af32-759e-41e2-b227-9eeb8576d609");
    static final UUID ME = UUID.fromString("835b345e-23d8-408d-a931-3adee61ca1d4");
    private static final int ARENA = 15;
    private static final int FLOOR = -61;
    private static final int WALL_TOP = -50;

    private GalleryGameTest() {
    }

    /** Called by SurfaceGameTest in place of the tests, since only one test may own the game. */
    static void run(ClientGameTestContext context) {
        context.waitFor(client -> client.gui.screen() instanceof dev.tierprofiles.ui.FirstRunScreen, 600);
        context.runOnClient(client -> {
            TestWorld.acceptNotice();
            // Card styles stay off: others' looks would come from earlier test saves, which
            // aren't anything those players chose. Your own card still shows yours.
            change(config -> config.global.cardStyles = false);
        });
        context.setScreen(() -> null);
        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(3);

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            buildArena(world.getServer());
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();

            context.runOnClient(client -> {
                client.options.guiScale().set(3);
                client.resizeGui();
                change(config -> style(config));
                TestWorld.addDuelist(client, SWIGHT, "Swight", -2.2, 5.5, 25, "netherite_sword");
                TestWorld.addDuelist(client, TestWorld.ITZ, "ItzRealMe", 2.4, 6, -30, "netherite_axe");
                TestWorld.addDuelist(client, TestWorld.JOKING, "JokingDK", 0.3, 10, 0, "mace");
            });
            waitForProfile(context, SWIGHT);
            waitForProfile(context, TestWorld.ITZ);
            waitForProfile(context, TestWorld.JOKING);
            context.waitTicks(60);
            context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
            context.waitTick();
            context.takeScreenshot("gallery-01-arena-nametags");

            context.runOnClient(client -> CardState.open(SWIGHT, "Swight"));
            context.waitTicks(40);
            context.takeScreenshot("gallery-02-card");

            context.runOnClient(client -> CardState.open(SWIGHT, "Swight", true));
            context.waitTicks(50);
            context.takeScreenshot("gallery-03-all-ranks");
            context.runOnClient(client -> CardState.close());
            context.waitTicks(20);

            TestInput input = context.getInput();
            input.holdKey(options -> options.keyPlayerList);
            context.waitTicks(5);
            input.pressKey(Keys.DOWN);
            input.pressKey(Keys.DOWN);
            context.waitTicks(15);
            context.takeScreenshot("gallery-04-tab-browsing");
            input.pressKey(Keys.PIN);
            context.waitTicks(30);
            context.takeScreenshot("gallery-05-tab-card");
            input.releaseKey(options -> options.keyPlayerList);
            context.runOnClient(client -> CardState.close());
            context.waitTicks(20);

            context.runOnClient(client -> CardState.open(ME, "Hoseinplays"));
            context.waitTicks(60);
            context.takeScreenshot("gallery-06-my-card");
            context.runOnClient(client -> CardState.close());
            context.waitTicks(20);

            for (SettingsTab tab : List.of(SettingsTab.TAGS, SettingsTab.PROFILE, SettingsTab.COLOURS)) {
                context.setScreen(() -> {
                    SettingsScreen screen = new SettingsScreen(null);
                    screen.openTab(tab);
                    return screen;
                });
                context.waitTicks(40);
                context.takeScreenshot("gallery-07-settings-" + tab.name().toLowerCase(java.util.Locale.ROOT));
            }
            context.setScreen(() -> null);
        }
    }

    private static void waitForProfile(ClientGameTestContext context, UUID uuid) {
        context.waitFor(client -> TierProfiles.profiles().profile(uuid) != null, 600);
    }

    private static void change(Consumer<ConfigFile> edit) {
        ConfigFile config = ConfigStore.copy(TierProfiles.config());
        edit.accept(config);
        TierProfiles.publish(config.sanitize());
    }

    /** Your own card, dressed up the way a player might pick it. */
    private static void style(ConfigFile config) {
        var look = config.global.cosmetics;
        look.background = "moon";
        look.border = "shimmer";
        look.effect = "sparkles";
        look.helmet = "netherite";
        look.chestplate = "netherite";
        look.leggings = "netherite";
        look.boots = "netherite";
        for (int slot = 0; slot < 4; slot++) {
            look.setTrim(slot, "silence");
            look.setTrimMaterial(slot, "diamond");
        }
    }

    /** A walled stone arena, so the shots look like a duels map instead of a flat test world. */
    private static void buildArena(TestServerContext server) {
        int a = ARENA;
        int w = a + 1;
        List<String> commands = List.of(
                "difficulty peaceful",
                "time set 1500",
                "weather clear",
                "kill @e[type=!player]",
                // Floor: polished andesite with a stone-brick border and a chiseled centre ring.
                fill(-a, FLOOR, -a, a, FLOOR, a, "polished_andesite"),
                fill(-a, FLOOR, -a, a, FLOOR, -a, "stone_bricks"),
                fill(-a, FLOOR, a, a, FLOOR, a, "stone_bricks"),
                fill(-a, FLOOR, -a, -a, FLOOR, a, "stone_bricks"),
                fill(a, FLOOR, -a, a, FLOOR, a, "stone_bricks"),
                fill(-3, FLOOR, -3, 3, FLOOR, 3, "chiseled_stone_bricks"),
                fill(-2, FLOOR, -2, 2, FLOOR, 2, "polished_deepslate"),
                fill(-a, FLOOR, -1, a, FLOOR, 1, "smooth_stone"),
                fill(-1, FLOOR, -3, 1, FLOOR, 3, "polished_deepslate"),
                // Walls.
                fill(-w, FLOOR, -w, w, WALL_TOP, -w, "stone_bricks"),
                fill(-w, FLOOR, w, w, WALL_TOP, w, "stone_bricks"),
                fill(-w, FLOOR, -w, -w, WALL_TOP, w, "stone_bricks"),
                fill(w, FLOOR, -w, w, WALL_TOP, w, "stone_bricks"),
                // A dark band and some wear, so the walls aren't one flat colour.
                fill(-w, -57, -w, w, -57, -w, "polished_blackstone_bricks"),
                fill(-w, -57, w, w, -57, w, "polished_blackstone_bricks"),
                fill(-w, -57, -w, -w, -57, w, "polished_blackstone_bricks"),
                fill(w, -57, -w, w, -57, w, "polished_blackstone_bricks"),
                fill(-9, -60, -w, -6, -58, -w, "mossy_stone_bricks"),
                fill(5, -56, -w, 8, -54, -w, "cracked_stone_bricks"),
                fill(-w, -59, 3, -w, -56, 6, "mossy_stone_bricks"),
                fill(w, -55, -8, w, -53, -5, "cracked_stone_bricks"));
        for (String command : commands) {
            server.runCommand(command);
        }
        for (int along = -w; along <= w; along += 8) {
            pillar(server, along, -w);
            pillar(server, along, w);
            pillar(server, -w, along);
            pillar(server, w, along);
        }
        // Stand at the south end, looking north across the arena.
        server.runCommand("tp @p 0 " + (FLOOR + 1) + " 9 180 0");
        server.runCommand("clear @p");
        server.runCommand("item replace entity @p weapon.mainhand with netherite_sword");
        server.runCommand("item replace entity @p hotbar.1 with golden_apple 16");
        server.runCommand("item replace entity @p hotbar.2 with ender_pearl 16");
        server.runCommand("item replace entity @p hotbar.3 with totem_of_undying");
        server.runCommand("item replace entity @p hotbar.4 with cobweb 32");
        server.runCommand("item replace entity @p hotbar.5 with water_bucket");
        server.runCommand("item replace entity @p hotbar.8 with cooked_beef 64");
        server.runCommand("item replace entity @p armor.head with netherite_helmet");
        server.runCommand("item replace entity @p armor.chest with netherite_chestplate");
        server.runCommand("item replace entity @p armor.legs with netherite_leggings");
        server.runCommand("item replace entity @p armor.feet with netherite_boots");
    }

    private static void pillar(TestServerContext server, int x, int z) {
        server.runCommand(fill(x, FLOOR, z, x, WALL_TOP + 1, z, "polished_deepslate"));
        server.runCommand("setblock " + x + " " + (WALL_TOP + 2) + " " + z + " lantern");
    }

    private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block;
    }
}
