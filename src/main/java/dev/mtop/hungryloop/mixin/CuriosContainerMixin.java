package dev.mtop.hungryloop.mixin;

import dev.mtop.hungryloop.curio.LoopSlotMirror;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hides the extra Curios slots the loop grants from the Curios screen. {@code setPage} builds one
 * GUI slot per slot in each type's stack handler; this trims the count it sees to just the leading
 * (real) slots, so the trailing loop-mirror slots get no GUI slot at all.
 *
 * <p>The mirror stacks themselves stay in the handler - only the on-screen slot is removed - so
 * Relics, Artifacts and Apotheosis still see them equipped. Mirrors are always placed in the
 * trailing slots by {@link LoopSlotMirror}, so "count of non-mirror slots" is exactly the run of
 * real ones.
 */
@Mixin(CuriosContainer.class)
public abstract class CuriosContainerMixin {

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
