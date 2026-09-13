package dev.mtop.hungryloop.mixin;

import dev.mtop.hungryloop.curio.LoopSlotMirror;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import top.theillusivec4.curios.common.CuriosConfig;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Closes the gaps the loop's hidden (mirror) slots would otherwise leave in the Curios panel.
 *
 * <p>{@code CurioSlotMixin.isActive()} stops a mirror slot rendering, but {@code setPage} has already
 * spent a grid cell on it, so the panel grows and the player's own curios shuffle around. This runs
 * <em>after</em> {@code setPage} and only rewrites presentation - each slot's {@code x}/{@code y},
 * plus {@code grid} and {@code panelWidth} - laying the visible slots out contiguously and parking
 * the hidden ones off-panel.
 *
 * <p>Deliberately <b>not</b> done by removing slots or changing how many {@code setPage} builds: the
 * container's slot list is index-addressed by vanilla's container sync, so it has to be identical on
 * both sides. Positions are client-render only, so if the client is briefly a mirror behind, the
 * worst case is a layout that corrects itself on the next rebuild - never a misplaced item.
 */
@Mixin(CuriosContainer.class)
public abstract class CuriosPanelLayoutMixin {

    /** Far enough off-panel that a parked slot can't be drawn or hovered, without overflowing. */
    private static final int PARKED = LoopSlotMirror.PARKED;

    @Shadow public int totalPages;
    @Shadow public int panelWidth;
    @Shadow public List<Integer> grid;

    @Inject(method = "setPage", at = @At("TAIL"), remap = false)
    private void hungryloop$compactHiddenSlots(int page, CallbackInfo ci) {
        List<CurioSlot> visible = new ArrayList<>();
        List<CurioSlot> hidden = new ArrayList<>();

        for (Slot slot : ((AbstractContainerMenu) (Object) this).slots) {
            if (slot instanceof CurioSlot curioSlot) {
                (LoopSlotMirror.isMirror(curioSlot.getItem()) ? hidden : visible).add(curioSlot);
            }
        }
        if (hidden.isEmpty()) return; // nothing of ours on screen - leave Curios' layout untouched

        // Multi-page panels index into a page window we'd have to recompute too; leave those be.
        if (this.totalPages > 1) return;

        int count = visible.size();
        int columns = Mth.clamp((int) Math.ceil(count / 8.0),
                Math.min(count, CuriosConfig.SERVER.minimumColumns.get()), 8);
        this.panelWidth = 14 + 18 * columns;

        // Mirrors setPage's own placement, over the visible slots only.
        this.grid.clear();
        int column = 1;
        int row = 1;
        for (CurioSlot slot : visible) {
            slot.x = (column - 1) * 18 + 7 - this.panelWidth;
            slot.y = 8 + (row - 1) * 18;

            if (this.grid.size() < column) {
                this.grid.add(1);
            } else {
                this.grid.set(column - 1, this.grid.get(column - 1) + 1);
            }

            if (column == columns) {
                column = 1;
                row++;
            } else {
                column++;
            }
        }

        for (CurioSlot slot : hidden) {
            slot.x = PARKED;
            slot.y = PARKED;
        }
    }
}
