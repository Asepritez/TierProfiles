package dev.tierprofiles;

import com.mojang.authlib.exceptions.AuthenticationException;
import dev.tierprofiles.core.net.CosmeticsService;
import java.io.IOException;
import net.minecraft.client.Minecraft;

/**
 * The one place the access token is used. This is the same call the game makes when you
 * join an online server, and the token goes straight to Mojang through the game's own
 * session service. Nothing else in the mod ever reads it.
 */
final class MojangJoiner implements CosmeticsService.SessionJoiner {
    @Override
    public void join(String serverId) throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            minecraft.services().sessionService().joinServer(
                    minecraft.getUser().getProfileId(), minecraft.getUser().getAccessToken(), serverId);
        } catch (AuthenticationException e) {
            throw new IOException("Mojang refused the session", e);
        }
    }
}
