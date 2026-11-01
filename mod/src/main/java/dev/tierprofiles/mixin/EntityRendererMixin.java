package dev.tierprofiles.mixin;

import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.surface.Decorator;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game only calls getNameTag once it has decided a name should show, so this
 * can't reveal a hidden nametag or an invisible player.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void tierprofiles$addTag(Entity entity, CallbackInfoReturnable<Component> result) {
        Component name = result.getReturnValue();
        if (name != null && entity instanceof Player player) {
            result.setReturnValue(Decorator.name(player.getUUID(), name, Surface.NAMETAG));
        }
    }
}
