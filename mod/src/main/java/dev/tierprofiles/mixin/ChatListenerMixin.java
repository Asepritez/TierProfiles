package dev.tierprofiles.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.logic.ChatSender;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.surface.Decorator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Adds chat tags to the text on your screen only. Signatures, the trust check and
 * the report log all still see the original message, because the change happens
 * after those, right at the call that displays it.
 */
@Mixin(ChatListener.class)
abstract class ChatListenerMixin {
    @WrapOperation(
            method = "showMessageToPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"))
    private void tierprofiles$tagPlayerChat(
            ChatComponent chat,
            Component message,
            MessageSignature signature,
            GuiMessageTag tag,
            Operation<Void> original,
            @Local(argsOnly = true) GameProfile sender) {
        original.call(chat, Decorator.chat(sender.id(), message), signature, tag);
    }

    /** Many servers send chat as system messages with no sender, so we look for a name. */
    @ModifyVariable(method = "handleSystemMessage", at = @At("HEAD"), argsOnly = true)
    private Component tierprofiles$tagSystemChat(Component message) {
        if (!TierProfiles.settings().surface(Surface.CHAT).enabled) {
            return message;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return message;
        }

        Map<String, UUID> players = new HashMap<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            players.put(info.getProfile().name(), info.getProfile().id());
        }
        String sender = ChatSender.find(message.getString(), players.keySet());
        return sender == null ? message : Decorator.chat(players.get(sender), message);
    }
}
