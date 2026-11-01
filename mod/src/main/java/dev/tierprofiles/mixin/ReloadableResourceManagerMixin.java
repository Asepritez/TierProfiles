package dev.tierprofiles.mixin;

import dev.tierprofiles.surface.IconPack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Adds our in-memory icon pack each time the game loads its resources. */
@Mixin(ReloadableResourceManager.class)
abstract class ReloadableResourceManagerMixin {
    @Shadow
    @Final
    private PackType type;

    @ModifyVariable(method = "createReload", at = @At("HEAD"), argsOnly = true)
    private List<PackResources> tierprofiles$addIconPack(List<PackResources> packs) {
        if (type != PackType.CLIENT_RESOURCES) {
            return packs;
        }
        List<PackResources> withOurs = new ArrayList<>(packs);
        withOurs.add(IconPack.INSTANCE);
        return withOurs;
    }
}
