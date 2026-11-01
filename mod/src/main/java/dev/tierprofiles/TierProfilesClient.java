package dev.tierprofiles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class TierProfilesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TierProfiles.start();

        LookupDriver lookups = new LookupDriver();
        ClientTickEvents.END_CLIENT_TICK.register(lookups::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TierProfiles.profiles().stop());
    }
}
