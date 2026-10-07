package com.pathetictry.packhub.mixin;

import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pathetictry.packhub.PackHubMod;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;

/**
 * Hides the Pack Hub packs from Options > Resource Packs. They still load and stay in whatever on/off
 * state /packs put them in; they are only left out of the two lists that screen draws.
 *
 * require = 0 on purpose: if a Minecraft update renames these methods, the packs simply show up in the
 * menu again instead of the game failing to start.
 */
@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelMixin {
    @Inject(method = "getUnselected", at = @At("RETURN"), cancellable = true, require = 0)
    private void packhub$hideUnselected(CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> cir) {
        cir.setReturnValue(cir.getReturnValue().filter(e -> !PackHubMod.isHubPackId(e.getId())));
    }

    @Inject(method = "getSelected", at = @At("RETURN"), cancellable = true, require = 0)
    private void packhub$hideSelected(CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> cir) {
        cir.setReturnValue(cir.getReturnValue().filter(e -> !PackHubMod.isHubPackId(e.getId())));
    }
}
