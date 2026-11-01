package dev.tierprofiles.mixin;

import java.util.List;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reads private fields of the tab list, so our order matches what's on screen. */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Accessor("visible")
    boolean tierprofiles$isVisible();

    @Invoker("getPlayerInfos")
    List<PlayerInfo> tierprofiles$players();
}
