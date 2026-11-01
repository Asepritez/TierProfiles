package dev.tierprofiles.test;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.hud.CardState;
import dev.tierprofiles.input.Keys;
import dev.tierprofiles.input.TabSelection;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;

/** Plays through the surfaces and the Tab controls in a real game window. */
public final class SurfaceGameTest implements FabricClientGameTest {
    private static final int LOOKUP_TIMEOUT_TICKS = 400;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> TestWorld.acceptNotice());

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();

            context.runOnClient(client -> {
                TestWorld.addPlayer(client, TestWorld.JOKING, "JokingDK", -2, 5);
                TestWorld.addPlayer(client, TestWorld.ITZ, "ItzRealMe", 2, 5);
                TestWorld.addPlayer(client, TestWorld.NOBODY, "zqxjwvkq_9x", 0, 7);
            });

            waitForProfile(context, TestWorld.JOKING);
            waitForProfile(context, TestWorld.ITZ);
            waitForProfile(context, TestWorld.NOBODY);
            assertRealData();

            context.waitTicks(10);
            context.takeScreenshot("tp-01-nametags");

            testIconsAreRealGameArt(context);
            testTabControls(context);
            testAnimationFrames(context);
        }
    }

    /** Compares the actual image bytes, so this can't pass just because something showed up. */
    private static void testIconsAreRealGameArt(ClientGameTestContext context) {
        context.runOnClient(client -> {
            check(client.getResourcePackRepository().getPack("programmer_art") != null, "Programmer Art should exist");

            byte[] classic = icon(client, "classic/dia_smp");
            byte[] current = icon(client, "current/dia_smp");
            check(!java.util.Arrays.equals(classic, current), "Classic and Current should be different art");

            byte[] own = icon(client, "classic/shield");
            check(own.length > 0, "a kit with no flat texture should fall back to our art");
        });
    }

    private static byte[] icon(net.minecraft.client.Minecraft client, String name) {
        var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("tierprofiles", "textures/icon/" + name + ".png");
        try (var in = client.getResourceManager().getResourceOrThrow(id).open()) {
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Freezes time at exact moments, so each screenshot shows a known animation phase. */
    private static void testAnimationFrames(ClientGameTestContext context) {
        long start = 5_000_000;
        long[] frozen = {start};
        context.runOnClient(client -> {
            CardState.setClock(() -> frozen[0]);
            CardState.open(TestWorld.JOKING, "JokingDK");
        });

        for (long at : new long[] {40, 100, 170, 280, 400, 700}) {
            frozen[0] = start + at;
            context.waitTick();
            context.takeScreenshot("tp-anim-in-" + at + "ms");
        }
        check(context.computeOnClient(client -> CardState.progress()) > 0.99f, "the card should have landed");

        context.runOnClient(client -> CardState.close());
        check(context.computeOnClient(client -> !CardState.isOpen()), "closing counts straight away");
        check(context.computeOnClient(client -> CardState.isVisible()), "the card should still be leaving");

        long closing = frozen[0];
        for (long at : new long[] {60, 140}) {
            frozen[0] = closing + at;
            context.waitTick();
            context.takeScreenshot("tp-anim-out-" + at + "ms");
        }
        frozen[0] = closing + 400;
        context.waitTick();
        check(context.computeOnClient(client -> !CardState.isVisible()), "the card should be gone");

        context.runOnClient(client -> CardState.useRealClock());
    }

    private static void waitForProfile(ClientGameTestContext context, UUID uuid) {
        context.waitFor(client -> TierProfiles.profiles().profile(uuid) != null, LOOKUP_TIMEOUT_TICKS);
    }

    private static void assertRealData() {
        PlayerProfile joking = TierProfiles.profiles().profile(TestWorld.JOKING);
        check(joking.list(TierList.MCTIERS).status() == ListStatus.OK, "MCTiers should know JokingDK");
        check(!joking.list(TierList.MCTIERS).tiers().isEmpty(), "JokingDK should have tiers");

        PlayerProfile nobody = TierProfiles.profiles().profile(TestWorld.NOBODY);
        check(nobody.list(TierList.MCTIERS).status() == ListStatus.UNRANKED, "the made-up player should be unranked");
    }

    private static void testTabControls(ClientGameTestContext context) {
        TestInput input = context.getInput();

        input.holdKey(options -> options.keyPlayerList);
        context.waitTicks(5);
        check(context.computeOnClient(client -> TabSelection.isListVisible()), "the tab list should be showing");
        context.takeScreenshot("tp-02-tab-list");

        input.pressKey(Keys.DOWN);
        input.pressKey(Keys.DOWN);
        context.waitTicks(3);
        check(context.computeOnClient(client -> TabSelection.selected() != null), "arrows should select a row");
        check(context.computeOnClient(client -> client.gui.screen() == null), "selecting must not open a Screen");
        context.takeScreenshot("tp-03-tab-selected");

        input.pressKey(Keys.PIN);
        context.waitTicks(10);
        check(context.computeOnClient(client -> CardState.isOpen()), "Enter should pin the card");
        check(context.computeOnClient(client -> client.gui.screen() == null), "the card must not be a Screen");
        context.takeScreenshot("tp-04-card-with-tab");

        input.releaseKey(options -> options.keyPlayerList);
        context.waitTicks(10);
        context.takeScreenshot("tp-05-card-on-hud");

        Vec3 before = context.computeOnClient(client -> client.player.position());
        input.holdKeyFor(options -> options.keyUp, 20);
        Vec3 after = context.computeOnClient(client -> client.player.position());
        check(before.distanceTo(after) > 1, "walking must keep working with the card open");
        check(context.computeOnClient(client -> CardState.isOpen()), "walking must not close the card");

        input.pressKey(Keys.PIN);
        context.waitTicks(3);
        check(context.computeOnClient(client -> !CardState.isOpen()), "Enter should close the card");

        context.runOnClient(client -> {
            CardState.open(TestWorld.JOKING, "JokingDK");
        });
        input.pressKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(
                com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE));
        context.waitTicks(3);
        check(context.computeOnClient(client -> !CardState.isOpen()), "Escape should close the card");
        check(context.computeOnClient(client -> client.gui.screen() == null), "Escape must not open the pause menu");
        context.waitTicks(20);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
