package dev.tierprofiles.mixin;

import dev.tierprofiles.input.Controls;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While Tab is held, the scroll wheel moves through players instead of the hotbar. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void tierprofiles$onScroll(long window, double xOffset, double yOffset, CallbackInfo callback) {
        if (Controls.onScroll(yOffset)) {
            callback.cancel();
        }
    }
}
