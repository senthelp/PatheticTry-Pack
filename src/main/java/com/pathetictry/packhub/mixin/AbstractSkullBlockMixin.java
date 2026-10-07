package com.pathetictry.packhub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pathetictry.packhub.PackHubMod;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;

@Mixin(AbstractSkullBlock.class)
public abstract class AbstractSkullBlockMixin {
    @Inject(method = "getType", at = @At("RETURN"), cancellable = true)
    private void packhub$swapType(CallbackInfoReturnable<SkullBlock.Type> cir) {
        if (cir.getReturnValue() != SkullBlock.Types.SKELETON) return;
        if (!PackHubMod.skullSwapEnabled) return;
        // During game startup the client object doesn't exist yet: do nothing then.
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        // Only on the client render thread; the integrated server thread keeps the real type.
        if (mc.isSameThread()) {
            cir.setReturnValue(SkullBlock.Types.DRAGON);
        }
    }
}
