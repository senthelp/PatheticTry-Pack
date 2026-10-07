package com.pathetictry.packhub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pathetictry.packhub.PackHubMod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/**
 * Lets /packs switch the Dragon Head look OFF without a reload: while it is off, the Dragon Head
 * model, name and rarity that PackHubMod puts on the Skeleton Skull item are swapped back to the vanilla ones.
 * Only touches the client render thread, never the integrated server.
 *
 * require = 0 on purpose: if a Minecraft update renames these methods the game still starts, and the
 * toggle then only affects the placed skull block.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "get", at = @At("RETURN"), cancellable = true, require = 0)
    private <T> void packhub$revertModelAndRarity(DataComponentType<? extends T> type, CallbackInfoReturnable<T> cir) {
        if (PackHubMod.skullSwapEnabled) return;
        if (type != DataComponents.ITEM_MODEL && type != DataComponents.RARITY) return;
        if (!packhub$isClientSkull()) return;
        Object value = cir.getReturnValue();
        if (type == DataComponents.ITEM_MODEL && Identifier.withDefaultNamespace("dragon_head").equals(value)) {
            cir.setReturnValue((T) Identifier.withDefaultNamespace("skeleton_skull"));
        } else if (type == DataComponents.RARITY && value == Rarity.EPIC) {
            cir.setReturnValue((T) Rarity.UNCOMMON);
        }
    }

    @Inject(method = "getItemName", at = @At("RETURN"), cancellable = true, require = 0)
    private void packhub$revertName(CallbackInfoReturnable<Component> cir) {
        if (PackHubMod.skullSwapEnabled) return;
        if (!packhub$isClientSkull()) return;
        Component name = cir.getReturnValue();
        if (name != null && "Dragon Head".equals(name.getString())) {
            cir.setReturnValue(Component.translatable("item.minecraft.skeleton_skull"));
        }
    }

    private boolean packhub$isClientSkull() {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.is(Items.SKELETON_SKULL)) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.isSameThread();
    }
}
