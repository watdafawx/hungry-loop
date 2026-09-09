package dev.mtop.hungryloop.mixin;

import dev.mtop.hungryloop.curio.LoopSlotMirror;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the extra Curios slots the loop grants from the Curios screen. {@code setPage} builds one GUI
 * slot per slot in each type's handler; we trim that per-type count by the mirror count for the type
 * ({@link LoopSlotMirror#hiddenSlotCount}). The HEAD hook reconciles the mirrors first (server side)
 * so that count is accurate; {@code CurioSlotMixin.isActive()} is the belt-and-braces for anything
 * that slips past mid-sync.
 *
 * <p>The underlying stacks stay in the handler, so Relics/Artifacts/Apotheosis still see them.
 */
@Mixin(CuriosContainer.class)
public abstract class CuriosContainerMixin {

    @Shadow public Player player;

    @Inject(method = "setPage", at = @At("HEAD"), remap = false)
    private void hungryloop$syncMirrorsFirst(int page, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            LoopSlotMirror.sync(serverPlayer);
        }
    }

    @Redirect(
            method = "setPage",
            at = @At(value = "INVOKE",
                    target = "Ltop/theillusivec4/curios/api/type/inventory/IDynamicStackHandler;getSlots()I"),
            remap = false)
    private int hungryloop$hideGrantedSlots(IDynamicStackHandler stacks) {
        return Math.max(0, stacks.getSlots() - LoopSlotMirror.hiddenSlotCount(stacks));
    }
}
