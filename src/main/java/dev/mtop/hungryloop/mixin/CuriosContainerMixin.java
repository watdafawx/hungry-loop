package dev.mtop.hungryloop.mixin;

import dev.mtop.hungryloop.curio.LoopSlotMirror;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the extra Curios slots the loop grants from the Curios screen: {@code setPage} builds one GUI
 * slot per slot in each type's handler, so we trim that count by the number the loop grants for that
 * type. The trailing slots (mirror or not-yet-mirrored) get no GUI slot; the underlying stacks stay,
 * so Relics/Artifacts/Apotheosis still see them equipped.
 *
 * <p>A {@code setPage} HEAD inject also re-syncs the mirrors first, so they're in place on the first
 * render.
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
        int real = stacks.getSlots();
        var handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return real;

        for (var entry : handler.getCurios().entrySet()) {
            if (entry.getValue().getStacks() == stacks) {
                return Math.max(0, real - LoopSlotMirror.grantedCount(player, entry.getKey()));
            }
        }
        return real;
    }
}
