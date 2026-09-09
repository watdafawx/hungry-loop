package dev.mtop.hungryloop.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.mtop.hungryloop.curio.LoopSlotMirror;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
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
 * slot per slot in each type's handler; we trim that count by how many slots the loop grants for the
 * type ({@link LoopSlotMirror#grantedCount}). That's derived from the loop's own synced contents, so
 * it's right the instant the loop is equipped - it doesn't wait for the mirror stacks to arrive.
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
    private int hungryloop$hideGrantedSlots(IDynamicStackHandler stacks,
                                            @Local ICurioStacksHandler stacksHandler) {
        int real = stacks.getSlots();
        return Math.max(0, real - LoopSlotMirror.grantedCount(player, stacksHandler.getIdentifier()));
    }
}
