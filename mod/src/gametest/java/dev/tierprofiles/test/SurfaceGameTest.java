package dev.tierprofiles.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.config.ConfigStore;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.hud.CardState;
import dev.tierprofiles.input.Keys;
import dev.tierprofiles.input.TabSelection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Plays through the surfaces and the Tab controls in a real game window. */
public final class SurfaceGameTest implements FabricClientGameTest {
    private static final int LOOKUP_TIMEOUT_TICKS = 400;
    private static final int[][] WINDOWS = {{854, 480}, {1280, 720}, {1920, 1080}};
    private static final int[] GUI_SCALES = {2, 3, 4};

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.gui.screen() instanceof dev.tierprofiles.ui.FirstRunScreen, 600);
        context.waitTicks(20);
        SurfaceGameTest.check(context.computeOnClient(client -> client.gui.screen() instanceof dev.tierprofiles.ui.FirstRunScreen),
                "a fresh install should open the privacy notice");
        context.takeScreenshot("tp-00-first-launch-notice");
        context.runOnClient(client -> TestWorld.acceptNotice());
        context.setScreen(() -> null);

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
            testBigTabPreview(context);
            testAnimationFrames(context);
            testEveryScreenSize(context);
            testAnchors(context);
            SettingsGameTest.run(context);
        }
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

    /** Compares the actual image bytes, so this can't pass just because something showed up. */
    private static void testIconsAreRealGameArt(ClientGameTestContext context) {
        context.runOnClient(client -> {
            check(client.getResourcePackRepository().getPack("programmer_art") != null, "Programmer Art should exist");

            byte[] classic = icon(client, "classic/dia_smp");
            byte[] current = icon(client, "current/dia_smp");
            check(!Arrays.equals(classic, current), "Classic and Current should be different art");

            byte[] own = icon(client, "classic/shield");
            check(own.length > 0, "a kit with no flat texture should fall back to our art");
        });
    }

    private static byte[] icon(Minecraft client, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("tierprofiles", "textures/icon/" + name + ".png");
        try (var in = client.getResourceManager().getResourceOrThrow(id).open()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void change(Consumer<ConfigFile> edit) {
        ConfigFile config = ConfigStore.copy(TierProfiles.config());
        edit.accept(config);
        TierProfiles.publish(config.sanitize());
    }

    /** Every size the layout table promises, as real screenshots. */
    private static void testEveryScreenSize(ClientGameTestContext context) {
        TestInput input = context.getInput();
        context.runOnClient(client -> CardState.open(TestWorld.JOKING, "JokingDK"));
        context.runOnClient(client -> change(config -> config.active().card.overlayCloseSeconds = 0));

        for (int[] window : WINDOWS) {
            input.resizeWindow(window[0], window[1]);
            context.waitTicks(3);
            for (int scale : GUI_SCALES) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                    CardState.open(TestWorld.JOKING, "JokingDK");
                });
                context.waitTicks(30);
                context.takeScreenshot("tp-size-" + window[0] + "x" + window[1] + "-gui" + scale);
            }
        }

        for (CardSettings.Detail detail : CardSettings.Detail.values()) {
            context.runOnClient(client -> {
                change(config -> config.active().card.detail = detail);
                client.options.guiScale().set(2);
                client.resizeGui();
                CardState.open(TestWorld.JOKING, "JokingDK");
            });
            context.waitTicks(30);
            context.takeScreenshot("tp-detail-" + detail.name().toLowerCase(java.util.Locale.ROOT));
        }
        context.runOnClient(client -> change(config -> config.active().card.detail = CardSettings.Detail.STANDARD));

        for (CardSettings.LastColumn last : CardSettings.LastColumn.values()) {
            context.runOnClient(client -> {
                change(config -> config.active().card.lastColumn = last);
                CardState.open(TestWorld.JOKING, "JokingDK");
            });
            context.waitTicks(30);
            context.takeScreenshot("tp-last-" + last.name().toLowerCase(java.util.Locale.ROOT));
        }

        // Hiding a list and a kit everywhere, then the All ranks view of what's left.
        context.runOnClient(client -> {
            change(config -> {
                config.active().card.lastColumn = CardSettings.LastColumn.DATE;
                config.active().filters.disabledLists.add(dev.tierprofiles.core.model.TierList.PVPHQ);
                config.active().filters.hiddenKits.add("smp");
            });
            CardState.open(TestWorld.JOKING, "JokingDK");
        });
        context.waitTicks(30);
        context.takeScreenshot("tp-hidden-pvphq-smp");
        context.runOnClient(client -> CardState.open(TestWorld.JOKING, "JokingDK", true));
        context.waitTicks(40);
        context.takeScreenshot("tp-all-ranks");
        context.runOnClient(client -> {
            client.options.guiScale().set(4);
            client.resizeGui();
            CardState.open(TestWorld.JOKING, "JokingDK", true);
        });
        context.waitTicks(40);
        context.takeScreenshot("tp-all-ranks-gui4");
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
            change(config -> {
                config.active().filters.disabledLists.clear();
                config.active().filters.hiddenKits.clear();
            });
            CardState.close();
        });
    }

    private static void testAnchors(ClientGameTestContext context) {
        for (CardSettings.Anchor anchor : CardSettings.Anchor.values()) {
            context.runOnClient(client -> {
                change(config -> config.active().card.anchor = anchor);
                CardState.open(TestWorld.JOKING, "JokingDK");
            });
            context.waitTicks(20);
            context.takeScreenshot("tp-anchor-" + anchor.name().toLowerCase(java.util.Locale.ROOT));
        }
        context.runOnClient(client -> {
            change(config -> config.active().card.anchor = CardSettings.Anchor.TOP_RIGHT);
            CardState.close();
        });
        context.waitTicks(10);
    }

    /** Freezes time at exact moments, so each screenshot shows a known animation phase. */
    private static void testAnimationFrames(ClientGameTestContext context) {
        long start = 5_000_000;
        long[] frozen = {start};
        context.runOnClient(client -> {
            CardState.setClock(() -> frozen[0]);
            CardState.open(TestWorld.JOKING, "JokingDK");
        });

        for (long at : new long[] {60, 160, 300, 450, 700, 1100}) {
            frozen[0] = start + at;
            context.waitTick();
            context.takeScreenshot("tp-anim-in-" + at + "ms");
        }
        check(context.computeOnClient(client -> CardState.progress()) > 0.99f, "the card should have landed");

        context.runOnClient(client -> CardState.close());
        check(context.computeOnClient(client -> !CardState.isOpen()), "closing counts straight away");
        check(context.computeOnClient(client -> CardState.isVisible()), "the card should still be leaving");

        long closing = frozen[0];
        for (long at : new long[] {120, 230}) {
            frozen[0] = closing + at;
            context.waitTick();
            context.takeScreenshot("tp-anim-out-" + at + "ms");
        }
        frozen[0] = closing + 400;
        context.waitTick();
        check(context.computeOnClient(client -> !CardState.isVisible()), "the card should be gone");

        context.runOnClient(client -> CardState.useRealClock());
    }

    /** With the big card chosen, browsing Tab shows each player's full card, and Enter keeps it. */
    private static void testBigTabPreview(ClientGameTestContext context) {
        TestInput input = context.getInput();
        context.runOnClient(client -> change(config -> config.active().card.tabPreview = CardSettings.TabPreview.BIG));
        input.holdKey(options -> options.keyPlayerList);
        context.waitTicks(5);
        input.pressKey(Keys.DOWN);
        context.waitTicks(30);
        check(context.computeOnClient(client -> CardState.isPreview()), "moving through Tab should show the big card");
        context.takeScreenshot("tp-02c-tab-big-card");

        // A server with a tall footer leaves little room under the list. The card must shrink to fit, not run off the screen.
        context.runOnClient(client -> client.gui.hud.getTabList().setFooter(net.minecraft.network.chat.Component.literal(
                "play.example.net\nJOIN OUR DISCORD /discord\n\nline\nline\nline\nline\nline\nline\nline\nline\nline\nline")));
        context.waitTicks(30);
        check(context.computeOnClient(client -> {
            var rect = dev.tierprofiles.hud.ProfileCard.lastRect();
            return rect != null && rect.y() + rect.height() <= client.getWindow().getGuiScaledHeight()
                    && rect.x() >= 0 && rect.x() + rect.width() <= client.getWindow().getGuiScaledWidth();
        }), "the card under a tall tab list must stay on screen");
        context.takeScreenshot("tp-02d-tab-big-card-tall-footer");
        context.runOnClient(client -> client.gui.hud.getTabList().setFooter(
                net.minecraft.network.chat.Component.literal("play.example.net\nJOIN OUR DISCORD /discord")));

        input.releaseKey(options -> options.keyPlayerList);
        context.waitTicks(5);
        check(context.computeOnClient(client -> !CardState.isOpen()), "letting go of Tab puts a browsed card away");

        input.holdKey(options -> options.keyPlayerList);
        context.waitTicks(5);
        input.pressKey(Keys.DOWN);
        context.waitTicks(5);
        input.pressKey(Keys.PIN);
        context.waitTicks(3);
        check(context.computeOnClient(client -> CardState.isOpen() && !CardState.isPreview()), "Enter should keep the browsed card");
        input.releaseKey(options -> options.keyPlayerList);
        context.waitTicks(5);
        check(context.computeOnClient(client -> CardState.isOpen()), "a kept card stays after Tab is let go");

        context.runOnClient(client -> {
            CardState.close();
            change(config -> config.active().card.tabPreview = CardSettings.TabPreview.SMALL);
            TabSelection.clear();
        });
        context.waitTicks(10);
    }

    private static void testTabControls(ClientGameTestContext context) {
        TestInput input = context.getInput();

        // Servers put text under the list, like a Discord link. Our hints must sit below it.
        context.runOnClient(client -> client.gui.hud.getTabList().setFooter(
                net.minecraft.network.chat.Component.literal("play.example.net\nJOIN OUR DISCORD /discord")));
        input.holdKey(options -> options.keyPlayerList);
        context.waitTicks(5);
        check(context.computeOnClient(client -> TabSelection.isListVisible()), "the tab list should be showing");
        context.takeScreenshot("tp-02-tab-list");

        input.pressKey(Keys.DOWN);
        input.pressKey(Keys.DOWN);
        context.waitTicks(10);
        context.takeScreenshot("tp-02b-tab-small-card");
        check(context.computeOnClient(client -> TabSelection.selected() != null), "arrows should select a row");
        check(context.computeOnClient(client -> client.gui.screen() == null), "selecting must not open a Screen");
        context.takeScreenshot("tp-03-tab-selected");

        input.pressKey(Keys.PIN);
        context.waitTicks(10);
        check(context.computeOnClient(client -> CardState.isOpen()), "Enter should pin the card");
        check(context.computeOnClient(client -> client.gui.screen() == null), "the card must not be a Screen");
        context.takeScreenshot("tp-04-card-with-tab");

        input.releaseKey(options -> options.keyPlayerList);
        context.waitTicks(30);
        context.takeScreenshot("tp-05-card-on-hud");

        Vec3 before = context.computeOnClient(client -> client.player.position());
        input.holdKeyFor(options -> options.keyUp, 20);
        Vec3 after = context.computeOnClient(client -> client.player.position());
        check(before.distanceTo(after) > 1, "walking must keep working with the card open");
        check(context.computeOnClient(client -> CardState.isOpen()), "walking must not close the card");

        input.pressKey(Keys.ALL_RANKS);
        context.waitTicks(3);
        check(context.computeOnClient(client -> CardState.allRanks()), "the All ranks key should expand the open card");
        input.pressKey(Keys.ALL_RANKS);
        context.waitTicks(3);
        check(context.computeOnClient(client -> !CardState.allRanks()), "pressing it again should go back");

        input.pressKey(Keys.PIN);
        context.waitTicks(3);
        check(context.computeOnClient(client -> !CardState.isOpen()), "Enter should close the card");

        context.runOnClient(client -> CardState.open(TestWorld.JOKING, "JokingDK"));
        input.pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE));
        context.waitTicks(3);
        check(context.computeOnClient(client -> !CardState.isOpen()), "Escape should close the card");
        check(context.computeOnClient(client -> client.gui.screen() == null), "Escape must not open the pause menu");
        context.waitTicks(20);
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
