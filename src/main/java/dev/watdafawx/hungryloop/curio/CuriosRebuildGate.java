package dev.watdafawx.hungryloop.curio;

import net.minecraft.world.inventory.Slot;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.CuriosConfig;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;

import java.util.ArrayList;
import java.util.List;
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
 * non-cosmetic Curios screen, the page count is unchanged and the curio slots on screen are exactly,
 * in order, the (type, index) window {@code setPage} would build for the current page - each still
 * pointing at its handler's current stack handler - and every mirror is still parked. Paged panels
 * get the same treatment (packs with many slot types paginate, and rebuilt there too). {@code applySyncTag} deserializes into the same stack
 * handler objects but replaces the render and active-state <em>lists</em>, so a skip re-points each
 * slot at the new lists - otherwise render toggles would read a stale copy. Anything structural
 * (resize, a type appearing, handlers replaced by a full sync, cosmetic view, a page shift) rebuilds as
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
        if (!IN_MODIFIERS_SYNC.get() || container.isViewingCosmetics) return false;
        ICuriosItemHandler handler = container.curiosHandler;
        if (handler == null) return false;
        Map<String, ICurioStacksHandler> curios = handler.getCurios();

        // What setPage would build for the current page: every visible handler's slots, flattened in
        // map order, sliced to this page's window. A single-page panel is the whole list.
        List<String> all = new ArrayList<>();
        for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
            if (!entry.getValue().isVisible()) continue;
            for (int i = 0; i < entry.getValue().getSlots(); i++) all.add(entry.getKey() + "#" + i);
        }
        int perPage = CuriosConfig.SERVER.maxSlotsPerPage.get();
        int pages = (int) Math.ceil(all.size() / (double) perPage);
        if (pages != container.totalPages) return false;
        int from = container.currentPage * perPage;
        if (from > all.size()) return false;
        List<String> expected = all.subList(from, Math.min(all.size(), from + perPage));

        List<String> onScreen = new ArrayList<>();
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
            onScreen.add(curioSlot.getIdentifier() + "#" + curioSlot.getSlotIndex());
        }
        if (!onScreen.equals(expected)) return false;

        for (Slot slot : container.slots) {
            if (slot instanceof CurioSlot curioSlot) {
                ICurioStacksHandler stacks = curios.get(curioSlot.getIdentifier());
                ((CurioSlotRebind) curioSlot).hungryloop$rebind(stacks.getRenders(), stacks.getActiveStates());
            }
        }
        return true;
    }
}
