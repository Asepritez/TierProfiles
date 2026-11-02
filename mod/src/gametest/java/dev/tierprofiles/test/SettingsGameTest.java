package dev.tierprofiles.test;

import dev.tierprofiles.TierProfiles;
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
            look.trimPattern = "rib";
            look.trimMaterial = "amethyst";
        });
        shot(context, "profile-styled");
        onScreen(context, current -> current.setAdvanced(true));
        shot(context, "profile-advanced");

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
