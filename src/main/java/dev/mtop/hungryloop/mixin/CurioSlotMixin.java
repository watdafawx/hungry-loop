package dev.mtop.hungryloop.mixin;

import dev.mtop.hungryloop.curio.LoopSlotMirror;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Handles the Curios-screen slots that hold the loop's mirror stacks:
 *
 * <ul>
 *   <li>{@code isActive} is false - the slot doesn't render and can't be hovered or clicked. This is
 *   the actual "hide the granted slots" mechanism; it works wherever the mirror sits, so it doesn't
 *   depend on the client and server agreeing on slot positions. The container keeps the slot, so
 *   indices stay in sync.</li>
 *   <li>{@code mayPickup}/{@code mayPlace} are false too, as a second guard against pulling a mirror
 *   copy out (which the loop would just recreate - a dupe).</li>
 * </ul>
 *
 * <p>{@link CurioSlot} inherits these from {@link SlotItemHandler} and doesn't override them, so the
 * mixin adds the overrides (extending the real superclass to reach {@code super}).
 */
@Mixin(CurioSlot.class)
public abstract class CurioSlotMixin extends SlotItemHandler {

    private CurioSlotMixin(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }

    @Override
    public boolean isActive() {
        return !LoopSlotMirror.isMirror(getItem()) && super.isActive();
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
