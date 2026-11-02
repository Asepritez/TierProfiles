package dev.tierprofiles.test;

import dev.tierprofiles.ui.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Opens the real settings screen. */
final class SettingsGameTest {
    private SettingsGameTest() {
    }

    static void run(ClientGameTestContext context) {
        context.setScreen(() -> new SettingsScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("tp-settings-first-look");
        context.setScreen(() -> null);
    }
}
