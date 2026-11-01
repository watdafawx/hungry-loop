package dev.watdafawx.hungryloop.curio;

import net.minecraft.world.inventory.Slot;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * Skips the client Curios screen rebuild that {@code SPacketSyncModifiers} forces, but only when the
 * rebuild would reproduce the slot list it already has.
 *
 * <p>Why: Curios resyncs a slot type whenever a worn stack in it stops matching its previous-tick
 * copy, and Relics refreshes its items' data every few ticks - worn directly or through a loop. The
 * client handler answers every such packet with {@code resetSlots()}, which replaces every
 * {@code Slot} object on the screen; anything keying state on slot identity (immersive-ui's hover
 * animation) restarts. Measured by {@code client.SelfTest}'s idle-churn scenarios.
 *
 * <p>Why it's safe where the earlier "visible slot total unchanged" gate wasn't: it compares the
 * <em>structure</em> per slot, not a count across types. The skip happens only if, for the open,
 * single-page, non-cosmetic Curios screen, every curio slot still points at its handler's current
 * stack handler at a valid index, every visible handler has exactly as many slots on screen as it
 * holds, and every mirror is still parked. {@code applySyncTag} deserializes into the same stack
 * handler objects but replaces the render and active-state <em>lists</em>, so a skip re-points each
 * slot at the new lists - otherwise render toggles would read a stale copy. Anything structural
 * (resize, a type appearing, handlers replaced by a full sync, cosmetic view, paging) rebuilds as
 * before. {@code SelfTest}'s ghost, stale-layout, move and render checks guard this.
 */
public final class CuriosRebuildGate {

    private static final ThreadLocal<Boolean> IN_MODIFIERS_SYNC = ThreadLocal.withInitial(() -> false);

    private CuriosRebuildGate() {}

    public static void enterModifiersSync() {
        IN_MODIFIERS_SYNC.set(true);
    }

    public static void exitModifiersSync() {
        IN_MODIFIERS_SYNC.set(false);
    }

    /** True if the rebuild was redundant and has been replaced by rebinding the slots in place. */
    public static boolean trySkipRebuild(CuriosContainer container) {
        if (!IN_MODIFIERS_SYNC.get() || container.totalPages > 1 || container.isViewingCosmetics) return false;
        ICuriosItemHandler handler = container.curiosHandler;
        if (handler == null) return false;
        Map<String, ICurioStacksHandler> curios = handler.getCurios();

        Map<String, Integer> onScreen = new HashMap<>();
        for (Slot slot : container.slots) {
            if (!(slot instanceof CurioSlot curioSlot)) continue;
            ICurioStacksHandler stacks = curios.get(curioSlot.getIdentifier());
            if (stacks == null
                    || curioSlot.isCosmetic()
                    || curioSlot.getItemHandler() != stacks.getStacks()
                    || curioSlot.getSlotIndex() >= stacks.getSlots()
                    || LoopSlotMirror.isMirror(curioSlot.getItem()) != (curioSlot.x == LoopSlotMirror.PARKED)) {
                return false;
            }
            onScreen.merge(curioSlot.getIdentifier(), 1, Integer::sum);
        }
        for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
            int expected = entry.getValue().isVisible() ? entry.getValue().getSlots() : 0;
            if (onScreen.getOrDefault(entry.getKey(), 0) != expected) return false;
        }

        for (Slot slot : container.slots) {
            if (slot instanceof CurioSlot curioSlot) {
                ICurioStacksHandler stacks = curios.get(curioSlot.getIdentifier());
                ((CurioSlotRebind) curioSlot).hungryloop$rebind(stacks.getRenders(), stacks.getActiveStates());
            }
        }
        return true;
    }
}
