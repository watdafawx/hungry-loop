package dev.watdafawx.hungryloop.integration.accessories;

import dev.watdafawx.hungryloop.curio.LoopSlotMirror;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.menu.AccessoriesBasedSlot;

/**
 * Which Accessories menu slots belong to the loop. A granted slot is a row of two menu slots (the
 * accessory and its cosmetic twin) over one container index; the row is the loop's while the
 * accessory side holds a mirror, so both halves are hidden together.
 */
public final class AccessoriesMirrors {

    private AccessoriesMirrors() {}

    public static boolean isMirrorRow(AccessoriesBasedSlot slot) {
        return isMirrorAt(slot.accessoriesContainer, slot.getContainerSlot());
    }

    public static boolean isMirrorAt(AccessoriesContainer container, int index) {
        var accessories = container.getAccessories();
        return index >= 0 && index < accessories.getContainerSize()
                && LoopSlotMirror.isMirror(accessories.getItem(index));
    }
}
