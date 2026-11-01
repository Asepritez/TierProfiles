package dev.tierprofiles.mixin;

import dev.tierprofiles.input.Controls;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sees keys the moment they're pressed, so arrows work with no Screen open. Only
 * keys we actually use get swallowed, and movement keys never do.
 */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void tierprofiles$onKey(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (Controls.onKey(action, event)) {
            callback.cancel();
        }
    }
}
