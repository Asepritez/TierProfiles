package dev.tierprofiles.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.input.TabSelection;
import dev.tierprofiles.surface.Decorator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void tierprofiles$addTag(PlayerInfo info, CallbackInfoReturnable<Component> result) {
        Component decorated = Decorator.name(info.getProfile().id(), result.getReturnValue(), Surface.TAB);
        result.setReturnValue(decorated);
        TabSelection.remember(info, decorated);
    }

    /**
     * The header and footer lines a server sets. We only note how far down they reach,
     * so our key hints go below the footer instead of over it.
     */
    @WrapOperation(
            //? if >=26.1 {
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"))
            //?} else {
            /*method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"))
            *///?}
    private void tierprofiles$noteFooter(
            GuiGraphicsExtractor graphics, Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int color, Operation<Void> original) {
        TabSelection.noteServerLine(y + font.lineHeight);
        original.call(graphics, font, text, x, y, color);
    }

    /** Runs as each name is drawn, so the highlight sits behind the selected row. */
    @WrapOperation(
            //? if >=26.1 {
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
            //?} else {
            /*method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
            *///?}
    private void tierprofiles$highlightRow(
            GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, Operation<Void> original) {
        TabSelection.beforeRow(graphics, font, text, x, y);
        original.call(graphics, font, text, x, y, color);
    }
}
