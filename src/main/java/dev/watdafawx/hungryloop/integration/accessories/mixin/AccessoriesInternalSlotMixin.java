package dev.watdafawx.hungryloop.integration.accessories.mixin;

import dev.watdafawx.hungryloop.integration.accessories.AccessoriesMirrors;
import io.wispforest.accessories.menu.AccessoriesInternalSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Accessories counterpart of {@code CurioSlotMixin}: a slot whose row holds a loop mirror is
 * never active (not drawn, hovered or clicked) and never gives up or takes an item - pulling a mirror
 * copy out would be a dupe the loop then refills. Pure flags, so the menu's slot list stays the same
 * on client and server.
 */
@Mixin(value = AccessoriesInternalSlot.class, remap = false)
public abstract class AccessoriesInternalSlotMixin {

    private boolean hungryloop$mirror() {
        return AccessoriesMirrors.isMirrorRow((AccessoriesInternalSlot) (Object) this);
    }

    // Descriptor pinned: the class also has a builder isActive(Function) returning the slot.
    @Inject(method = "isActive()Z", at = @At("HEAD"), cancellable = true)
    private void hungryloop$hideMirror(CallbackInfoReturnable<Boolean> cir) {
        if (hungryloop$mirror()) cir.setReturnValue(false);
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void hungryloop$keepMirror(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (hungryloop$mirror()) cir.setReturnValue(false);
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void hungryloop$noPlaceOnMirror(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (hungryloop$mirror()) cir.setReturnValue(false);
    }

    @Inject(method = "allowModification", at = @At("HEAD"), cancellable = true)
    private void hungryloop$noModifyMirror(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (hungryloop$mirror()) cir.setReturnValue(false);
    }
}
