package dev.tierprofiles;

import dev.tierprofiles.hud.HudLayer;
import dev.tierprofiles.input.Keys;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class TierProfilesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TierProfiles.start();
        Keys.register();
        HudLayer.register();

        LookupDriver lookups = new LookupDriver();
        ClientTickEvents.END_CLIENT_TICK.register(lookups::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TierProfiles.profiles().stop());
    }
}
