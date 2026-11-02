package dev.tierprofiles;

import dev.tierprofiles.hud.HudLayer;
import dev.tierprofiles.input.Keys;
import dev.tierprofiles.ui.FirstRunScreen;
import dev.tierprofiles.ui.SettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

public final class TierProfilesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TierProfiles.start();
        Keys.register();
        HudLayer.register();

        LookupDriver lookups = new LookupDriver();
        ClientTickEvents.END_CLIENT_TICK.register(lookups::tick);
        ClientTickEvents.END_CLIENT_TICK.register(TierProfilesClient::openSettingsOnKey);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen && !TierProfiles.config().global.noticeSeen) {
                client.gui.setScreen(new FirstRunScreen(screen));
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TierProfiles.profiles().stop());
    }

    private static void openSettingsOnKey(Minecraft client) {
        while (Keys.SETTINGS.consumeClick()) {
            if (client.gui.screen() == null) {
                client.gui.setScreen(new SettingsScreen(null));
            }
        }
    }
}
