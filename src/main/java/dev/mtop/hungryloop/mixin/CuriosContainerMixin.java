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
 * Two jobs on the Curios screen:
 * <ol>
 *   <li>Before it rebuilds its slots, make sure the loop's mirror stacks are up to date, so the
 *       trailing granted slots are already filled (and therefore hidden) on the first render.</li>
 *   <li>Trim the per-type slot count {@code setPage} reads so those trailing mirror slots get no GUI
 *       slot at all. The stacks stay in the handler - Relics/Artifacts/Apotheosis still see them.</li>
 * </ol>
 * Mirrors are always the trailing slots ({@link LoopSlotMirror}), so "count of non-mirror slots" is
 * exactly the run of real ones.
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
    private int hungryloop$hideMirrorSlots(IDynamicStackHandler stacks) {
        int visible = stacks.getSlots();
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (LoopSlotMirror.isMirror(stacks.getStackInSlot(i))) {
                visible--;
            }
        }
        return visible;
    }
}
