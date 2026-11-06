package dev.watdafawx.hungryloop.integration.accessories;

import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.menu.variants.AccessoriesMenuBase;
import io.wispforest.accessories.networking.client.SyncEntireContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * With Accessories (and its Curios compat layer) installed, the Curios inventory is a view over
 * Accessories' containers and the compat layer drops every Curios sync packet on the client - so
 * {@code SPacketSyncCurios} no longer does anything. Accessories syncs on its own tick instead, and
 * its per-tick packet only carries a container's new size when the server side has just rebuilt it;
 * mirror writes landing between a resize and that packet left the client a slot short, with the
 * mirror then written past the end ("out of the range valid for the container").
 *
 * <p>Same remedy as the Curios path: after the loop changes the layout, push the whole holder
 * (sizes, modifiers, stacks) as one packet - the one Accessories itself uses on login - then reopen
 * an open Accessories screen server-side, which is what Accessories does after a full sync too, so
 * the menu's slot list is rebuilt on both sides from the same data.
 */
public final class AccessoriesSync {

    private AccessoriesSync() {}

    public static void fullSync(ServerPlayer player) {
        SyncEntireContainer.syncToAllTrackingAndSelf(player);
        if (player.containerMenu instanceof AccessoriesMenuBase menu) {
            // Hand the cursor stack to the new menu; closing the old one would otherwise drop it
            // back into the inventory mid-drag.
            ItemStack carried = menu.getCarried();
            menu.setCarried(ItemStack.EMPTY);
            Accessories.openAccessoriesMenu(player, menu.menuVariant(), menu.targetEntity(), carried);
        }
    }
}
