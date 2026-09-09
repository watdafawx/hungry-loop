package dev.mtop.hungryloop.mixin;

import dev.mtop.hungryloop.curio.LoopSlotMirror;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Makes the Curios-screen slot for a loop-managed mirror stack read-only, so the player edits loop
 * contents through the loop's own screen and never by reaching into the granted slot. Without this
 * they could pull a mirror copy out and the loop would recreate it next tick - a dupe.
 *
 * <p>{@link CurioSlot} inherits {@code mayPickup}/{@code mayPlace} from {@link SlotItemHandler} and
 * doesn't override them, so the mixin adds the overrides (extending the real superclass to reach
 * {@code super}).
 */
@Mixin(CurioSlot.class)
public abstract class CurioSlotMixin extends SlotItemHandler {

    private CurioSlotMixin(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }

    @Override
    public boolean mayPickup(Player player) {
        return !LoopSlotMirror.isMirror(getItem()) && super.mayPickup(player);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !LoopSlotMirror.isMirror(getItem()) && super.mayPlace(stack);
    }
}
