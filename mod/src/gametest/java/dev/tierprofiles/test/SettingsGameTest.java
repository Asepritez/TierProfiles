package dev.tierprofiles.test;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.net.CosmeticsService;
import dev.tierprofiles.core.settings.HighlightRule;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.ui.FirstRunScreen;
import dev.tierprofiles.ui.SettingsScreen;
import dev.tierprofiles.ui.SettingsTab;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

/** Opens the real settings screen and photographs every tab. */
final class SettingsGameTest {
    private static SettingsScreen screen;

    private SettingsGameTest() {
    }

    private static void onScreen(ClientGameTestContext context, Consumer<SettingsScreen> action) {
        context.runOnClient(client -> action.accept(screen));
        context.waitTicks(2);
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.waitTick();
        context.takeScreenshot("tp-config-" + name);
    }

    private static void rules(Settings settings) {
        HighlightRule top = HighlightRule.of(null, "HT1", 0xC58BFF);
        top.comparison = HighlightRule.Comparison.AT;
        top.bold = true;
        top.star = true;
        HighlightRule red = HighlightRule.of(null, "HT3", 0xFF7777);
        red.comparison = HighlightRule.Comparison.ABOVE;
        red.glow = true;
        settings.rules.add(top);
        settings.rules.add(red);
    }

    /**
     * The whole save path against the local server and a stand-in for Mojang. The joiner
     * here is a fake, so no real account or real Mojang call is involved.
     */
    private static void testSavingACard(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            TierProfiles.cosmetics().useJoiner(serverId -> { });
            screen.applyChanges();
        });
        String note = context.computeOnClient(minecraft -> screen.footerNoteForTest());
        SurfaceGameTest.check(note != null && note.contains("applied"), "Apply should say what it did, but said " + note);
        shot(context, "applied-note");
        context.waitFor(minecraft -> TierProfiles.cosmetics().state() != CosmeticsService.SaveState.SAVING, 200);
        CosmeticsService.SaveState state = context.computeOnClient(minecraft -> TierProfiles.cosmetics().state());
        SurfaceGameTest.check(state == CosmeticsService.SaveState.SAVED, "the card should save, but got " + state);

        context.runOnClient(minecraft -> TierProfiles.cosmetics().request(TestWorld.ITZ));
        context.waitFor(minecraft -> TierProfiles.cosmetics().get(TestWorld.ITZ) != null, 200);
        String background = context.computeOnClient(minecraft -> TierProfiles.cosmetics().get(TestWorld.ITZ).background);
        SurfaceGameTest.check(background.equals("moon"), "another client should see the saved card");

        context.runOnClient(minecraft -> {
            dev.tierprofiles.hud.CardState.open(TestWorld.ITZ, "ItzRealMe");
            minecraft.gui.setScreen(null);
        });
        context.waitTicks(40);
        context.takeScreenshot("tp-saved-card-seen-by-others");
        context.runOnClient(minecraft -> {
            dev.tierprofiles.hud.CardState.close();
            minecraft.gui.setScreen(screen);
        });
        context.waitTicks(3);
    }

    static void run(ClientGameTestContext context) {
        Minecraft client = context.computeOnClient(minecraft -> minecraft);
        context.runOnClient(minecraft -> {
            minecraft.options.guiScale().set(4);
            minecraft.resizeGui();
        });
        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(3);

        context.setScreen(() -> {
            screen = new SettingsScreen(null);
            return screen;
        });
        context.waitTicks(5);

        for (SettingsTab tab : SettingsTab.values()) {
            onScreen(context, current -> current.openTab(tab));
            shot(context, tab.name().toLowerCase(Locale.ROOT));
        }

        onScreen(context, current -> {
            current.openTab(SettingsTab.RULES);
            rules(current.stagedConfig().active());
            current.selectRule(1);
        });
        shot(context, "rules-editing");
        SurfaceGameTest.check(context.computeOnClient(minecraft -> screen.changeCount()) > 0, "rules should count as staged");

        onScreen(context, current -> {
            current.openTab(SettingsTab.PROFILE);
            var look = current.stagedConfig().global.cosmetics;
            look.background = "moon";
            look.border = "shimmer";
            look.effect = "sparkles";
            look.helmet = "netherite";
            look.chestplate = "netherite";
            look.leggings = "netherite";
            look.boots = "netherite";
            for (int slot = 0; slot < 4; slot++) {
                look.setTrim(slot, "rib");
                look.setTrimMaterial(slot, "amethyst");
            }
            // A different trim on the helmet, which older versions couldn't do.
            look.setTrim(0, "silence");
            look.setTrimMaterial(0, "gold");
            look.hiddenLists.add("pvptiers");
        });
        shot(context, "profile-styled");
        // The trims sit under the armor, outside Advanced, so scroll down to them.
        context.getInput().setCursorPos(600, 600);
        context.getInput().scroll(-20);
        context.waitTicks(2);
        shot(context, "profile-trims");
        context.getInput().scroll(20);
        onScreen(context, current -> current.setAdvanced(true));
        shot(context, "profile-advanced");
        testSavingACard(context);

        // Hovering shows what a setting does, beside the mouse. At 1080p one virtual pixel is two
        // real ones, so these land on the Lists rail tab and on the first Profile setting.
        onScreen(context, current -> current.setAdvanced(false));
        context.getInput().setCursorPos(100, 180);
        context.waitTicks(15);
        shot(context, "tooltip-rail");
        context.getInput().setCursorPos(300, 186);
        context.waitTicks(15);
        shot(context, "tooltip-setting");
        onScreen(context, current -> current.searchFor("tab"));
        context.waitTicks(5);
        shot(context, "search-tab");
        onScreen(context, current -> current.searchFor(""));

        // Hovering a Tab choice previews it with your own card.
        onScreen(context, current -> current.openTab(SettingsTab.CARDS));
        context.getInput().setCursorPos(520, 312);
        context.waitTicks(15);
        shot(context, "preview-tags-only");
        context.getInput().setCursorPos(670, 312);
        context.waitTicks(15);
        shot(context, "preview-full-profile");
        context.getInput().setCursorPos(5, 1075);

        onScreen(context, current -> {
            current.resetAllForTest();
            SurfaceGameTest.check(current.changeCount() > 0, "Reset should stage changes");
            SurfaceGameTest.check(current.stagedConfig().global.noticeSeen, "Reset must keep the privacy answer");
            current.discardForTest();
        });

        onScreen(context, current -> current.openTab(SettingsTab.MORE));
        onScreen(context, current -> current.openDiagnostics());
        shot(context, "diagnostics");

        onScreen(context, current -> current.searchFor("retired"));
        shot(context, "search");
        onScreen(context, current -> current.searchFor(""));

        onScreen(context, current -> current.openTab(SettingsTab.TAGS));
        onScreen(context, current -> current.stagedConfig().active().surface(Surface.NAMETAG).minimumTier = "LT3");
        shot(context, "tags-filtered");

        for (int[] window : new int[][] {{854, 480}, {1280, 720}}) {
            context.getInput().resizeWindow(window[0], window[1]);
            context.waitTicks(4);
            SurfaceGameTest.check(context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof SettingsScreen), "resizing must not close settings");
            shot(context, "size-" + window[0]);
        }

        context.setScreen(() -> new FirstRunScreen(null));
        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(4);
        context.takeScreenshot("tp-first-run");
        context.setScreen(() -> null);

        int settings = context.computeOnClient(minecraft -> {
            int count = new SettingsScreen(null).settingCount();
            TierProfiles.LOGGER.info("Settings in the catalog: {}", count);
            return count;
        });
        SurfaceGameTest.check(settings > 50, "the catalog should be full");
    }
}
