package dev.watdafawx.hungryloop.integration.accessories.mixin;

import dev.watdafawx.hungryloop.curio.LoopSlotMirror;
import dev.watdafawx.hungryloop.integration.accessories.AccessoriesMirrors;
import io.wispforest.accessories.menu.AccessoriesInternalSlot;
import io.wispforest.accessories.menu.variants.AccessoriesMenu;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.TreeMap;

/**
 * The original Accessories screen is one scrolling column: every slot gets a row index
 * ({@code slotToPageIndex}), rows {@code [scrolledIndex, scrolledIndex + 8)} are shown. A loop
 * mirror row is inactive (see {@link AccessoriesInternalSlotMixin}) but would still own a row - an
 * empty gap in the list. After the menu is built, renumber the rows skipping the mirror ones, park
 * those off-screen, and recompute the scroll bounds.
 *
 * <p>Presentation only, like {@code CuriosPanelLayoutMixin}: the slot list is untouched, so it stays
 * identical to the server's. Accessories rebuilds (reopens) this menu whenever a container resizes,
 * which is exactly when the loop's mirror rows come and go.
 */
@Mixin(value = AccessoriesMenu.class, remap = false)
public abstract class AccessoriesMenuLayoutMixin {

    /** Rows Accessories shows at once. */
    private static final int VISIBLE_ROWS = 8;
    /** A row index no scroll window can reach. */
    private static final int HIDDEN_ROW = Integer.MIN_VALUE / 2;

    @Shadow public int totalSlots;
    @Shadow public boolean overMaxVisibleSlots;
    @Shadow private int maxScrollableIndex;
    @Shadow @Final private Map<Integer, Boolean> slotToView;
    @Shadow private Map<AccessoriesInternalSlot, Integer> slotToPageIndex;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void hungryloop$compactMirrorRows(int containerId, Inventory inventory, LivingEntity targetEntity,
                                              CallbackInfo ci) {
        // old row -> whether it is a mirror row (the base, non-cosmetic slot decides)
        Map<Integer, Boolean> rows = new TreeMap<>();
        for (var entry : this.slotToPageIndex.entrySet()) {
            if (!entry.getKey().isCosmetic) rows.put(entry.getValue(), AccessoriesMirrors.isMirrorRow(entry.getKey()));
        }
        if (!rows.containsValue(true)) return; // nothing of ours - leave Accessories' layout alone

        Map<Integer, Integer> renumbered = new TreeMap<>();
        int next = 0;
        for (var row : rows.entrySet()) renumbered.put(row.getKey(), row.getValue() ? HIDDEN_ROW : next++);

        for (var entry : this.slotToPageIndex.entrySet()) {
            AccessoriesInternalSlot slot = entry.getKey();
            int row = renumbered.getOrDefault(entry.getValue(), HIDDEN_ROW);
            entry.setValue(row);
            // Same formula Accessories uses: currentY = row * 18 + minY(8) + 8
            slot.y = row == HIDDEN_ROW ? LoopSlotMirror.PARKED : row * 18 + 16;
            this.slotToView.put(slot.index, row != HIDDEN_ROW && row < VISIBLE_ROWS);
        }

        this.totalSlots = next;
        this.maxScrollableIndex = this.totalSlots - VISIBLE_ROWS;
        this.overMaxVisibleSlots = this.totalSlots > VISIBLE_ROWS;
    }
}
