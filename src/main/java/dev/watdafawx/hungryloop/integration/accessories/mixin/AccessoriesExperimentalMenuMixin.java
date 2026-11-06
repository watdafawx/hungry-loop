package dev.watdafawx.hungryloop.integration.accessories.mixin;

import dev.watdafawx.hungryloop.integration.accessories.AccessoriesMirrors;
import io.wispforest.accessories.api.menu.AccessoriesBasedSlot;
import io.wispforest.accessories.menu.variants.AccessoriesExperimentalMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * The experimental (owo) Accessories screen lays out exactly the slots this returns, as
 * (cosmetic, accessory) pairs. Drop the loop's mirror rows so the grid has no cells for them; the
 * slots themselves stay in the menu (parked off-screen at -300), keeping both sides' lists equal.
 */
@Mixin(value = AccessoriesExperimentalMenu.class, remap = false)
public abstract class AccessoriesExperimentalMenuMixin {

    @Inject(method = "getVisibleAccessoriesSlots", at = @At("RETURN"), cancellable = true)
    private void hungryloop$dropMirrorRows(CallbackInfoReturnable<List<Slot>> cir) {
        List<Slot> slots = cir.getReturnValue();
        List<Slot> kept = new ArrayList<>(slots.size());
        boolean dropped = false;
        for (int i = 0; i + 1 < slots.size(); i += 2) {
            if (slots.get(i + 1) instanceof AccessoriesBasedSlot accessory && AccessoriesMirrors.isMirrorRow(accessory)) {
                dropped = true;
                continue;
            }
            kept.add(slots.get(i));
            kept.add(slots.get(i + 1));
        }
        if (dropped) cir.setReturnValue(kept);
    }
}
