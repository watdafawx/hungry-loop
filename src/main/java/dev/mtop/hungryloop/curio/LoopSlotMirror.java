package dev.mtop.hungryloop.curio;

import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.item.HungryLoopItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.event.SlotModifiersUpdatedEvent;
import top.theillusivec4.curios.api.type.ICuriosMenu;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncRender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the real Curios slots the loop grants filled with the loop's contents, as marked mirror
 * copies. Reconciliation is incremental and content-matched - a mirror already holding a wanted item
 * is left where it is, so adding/removing one item never blips the others.
 *
 * <p>Each mirror slot's Curios render flag tracks the loop's toggles: {@link LoopContents#selfRenders}
 * (or the loop's own slot being hidden) hides every contained cosmetic; {@link LoopContents#renderMask}
 * hides individual ones.
 */
@EventBusSubscriber(modid = HungryLoop.MODID)
public final class LoopSlotMirror {

    private static final int SYNC_INTERVAL = 3;

    /**
     * Players whose mirrors are being reconciled right now. Writing a mirror stack makes Curios
     * recompute slot modifiers, which fires {@link SlotModifiersUpdatedEvent} again mid-sync; the
     * open Curios screen's {@code setPage} does the same. Without this guard those re-entrant calls
     * storm each other and the granted slots flicker. Any reconcile we skip is picked up by the
     * next tick sync.
     */
    private static final Set<UUID> RECONCILING = ConcurrentHashMap.newKeySet();

    private record Want(ItemStack stack, boolean render) {}

    private LoopSlotMirror() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % SYNC_INTERVAL == 0) {
            purgeStrayMirrors(player);
            sync(player, false);
        }
    }

    @SubscribeEvent
    static void onItemEntitySpawn(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity itemEntity && isMirror(itemEntity.getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onCurioChange(CurioChangeEvent event) {
        if (event.getFrom().getItem() instanceof HungryLoopItem
                && event.getEntity() instanceof ServerPlayer player) {
            // A loop was taken out of a slot. If it was the last one, purge every mirror; otherwise
            // reconcile against what's left.
            boolean anyLeft = CuriosApi.getCuriosInventory(player)
                    .map(h -> !h.findCurios(s -> s.getItem() instanceof HungryLoopItem && !isMirror(s)).isEmpty())
                    .orElse(false);
            if (anyLeft) {
                sync(player, true);
            } else if (RECONCILING.add(player.getUUID())) {
                try {
                    purgeAllMirrors(player);
                } finally {
                    RECONCILING.remove(player.getUUID());
                }
            }
        }
    }

    @SubscribeEvent
    static void onSlotModifiersUpdated(SlotModifiersUpdatedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, true); // slot counts changed - reconcile the mirrors now
        }
    }

    public static void sync(ServerPlayer player) {
        sync(player, true);
    }

    public static void sync(ServerPlayer player, boolean force) {
        if (!RECONCILING.add(player.getUUID())) return; // already reconciling - avoid the feedback storm
        try {
            reconcile(player);
        } finally {
            RECONCILING.remove(player.getUUID());
        }
    }

    private static void reconcile(ServerPlayer player) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return;

        List<SlotResult> loops =
                handler.findCurios(s -> s.getItem() instanceof HungryLoopItem && !isMirror(s));

        // No loop found. This is either a genuine unequip - handled explicitly by onCurioChange,
        // which purges every mirror - or a transient blip while Curios rebuilds the inventory
        // (equipping into a slot type mid-resize). Either way, don't touch the mirrors here: a
        // reconcile that clears everything and the next that refills it is exactly the flicker.
        if (loops.isEmpty()) return;

        Map<String, List<Want>> wanted = new LinkedHashMap<>();
        for (SlotResult worn : loops) {
            LoopContents data = worn.stack()
                    .getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
            boolean loopVisible = data.selfRenders() && slotRenders(handler,
                    worn.slotContext().identifier(), worn.slotContext().index());

            List<ItemStack> items = data.items();
            for (int i = 0; i < items.size(); i++) {
                ItemStack contained = items.get(i);
                // Skip empties and nested loops - a mirrored loop would be picked up as a worn loop
                // on the next pass and mirror its own contents, spawning phantom copies.
                if (contained.isEmpty() || contained.getItem() instanceof HungryLoopItem) continue;
                wanted.computeIfAbsent(ContentForwarding.resolveSlot(contained, player), k -> new ArrayList<>())
                        .add(new Want(contained, loopVisible && data.rendersItem(i)));
            }
        }

        boolean[] changed = {false};
        handler.getCurios().forEach((type, stacksHandler) -> {
            if (mirrorInto(player, type, stacksHandler, wanted.getOrDefault(type, List.of()))) {
                changed[0] = true;
            }
        });

        // The client's Curios screen only re-hides the granted slots when it rebuilds. Nudge it to
        // do so once the mirror layout has actually changed, so the slots don't linger visible until
        // the player closes and reopens the screen.
        if (changed[0] && player.containerMenu instanceof ICuriosMenu menu) {
            menu.resetSlots();
        }
    }

    /** Wipe every mirror from every curio slot - used when the last loop is unequipped. */
    private static void purgeAllMirrors(ServerPlayer player) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return;
        handler.getCurios().forEach((type, sh) -> {
            var stacks = sh.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (isMirror(stacks.getStackInSlot(i))) {
                    stacks.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        });
    }

    private static boolean slotRenders(ICuriosItemHandler handler, String type, int index) {
        return handler.getStacksHandler(type)
                .map(sh -> index >= sh.getRenders().size() || sh.getRenders().get(index))
                .orElse(true);
    }

    /**
     * Mirrors {@code desired} into this type's <em>trailing</em> slots and nowhere else. Keeping the
     * mirrors packed at the high indices is what lets the Curios screen hide them by simply trimming
     * the slot count ({@link #hiddenSlotCount}).
     *
     * <p>A trailing slot that already holds a mirror of a still-wanted item type is left completely
     * alone - we only match on the item, not its data. Relics/Artifacts/Apotheosis mutate the live
     * mirror stack in place (cooldowns, energy, ...), so rewriting it from the loop's stored copy
     * every sync would wipe that state and make the slot flicker.
     */
    private static boolean mirrorInto(ServerPlayer player, String type, ICurioStacksHandler stacksHandler,
                                      List<Want> desired) {
        var stacks = stacksHandler.getStacks();
        int total = stacks.getSlots();
        boolean changed = false;

        // How many trailing slots we can actually claim: empty or already a mirror.
        int claimable = 0;
        for (int i = total - 1; i >= 0; i--) {
            ItemStack s = stacks.getStackInSlot(i);
            if (s.isEmpty() || isMirror(s)) claimable++;
            else break;
        }
        int k = Math.min(desired.size(), claimable);
        int firstMirror = total - k;

        List<Want> pending = new ArrayList<>(desired.subList(0, k));

        // Pass 1: keep a trailing slot whose mirror still matches a wanted item (by item, not data).
        boolean[] filled = new boolean[k];
        for (int p = 0; p < k; p++) {
            ItemStack cur = stacks.getStackInSlot(firstMirror + p);
            if (!isMirror(cur)) continue;
            for (int w = 0; w < pending.size(); w++) {
                if (ItemStack.isSameItem(cur, pending.get(w).stack())) {
                    setRender(player, stacksHandler, type, firstMirror + p, pending.remove(w).render());
                    filled[p] = true;
                    break;
                }
            }
        }

        // Pass 2: fill the trailing slots we didn't keep - a fresh mirror, or empty if nothing's left.
        for (int p = 0; p < k; p++) {
            if (filled[p]) continue;
            int idx = firstMirror + p;
            if (!pending.isEmpty()) {
                Want want = pending.remove(0);
                stacks.setStackInSlot(idx, mirrorCopy(want.stack()));
                setRender(player, stacksHandler, type, idx, want.render());
                changed = true;
            } else if (isMirror(stacks.getStackInSlot(idx))) {
                stacks.setStackInSlot(idx, ItemStack.EMPTY);
                changed = true;
            }
        }

        // Anything mirror-marked outside that trailing block is stale - clear it.
        for (int i = 0; i < firstMirror; i++) {
            if (isMirror(stacks.getStackInSlot(i))) {
                stacks.setStackInSlot(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        return changed;
    }

    /** Set a granted slot's Curios render flag and tell the client, if it changed. */
    private static void setRender(ServerPlayer player, ICurioStacksHandler stacksHandler, String type,
                                  int index, boolean render) {
        var renders = stacksHandler.getRenders();
        if (index >= renders.size() || renders.get(index) == render) return;
        renders.set(index, render);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new SPacketSyncRender(player.getId(), type, index, render));
    }

    /**
     * How many of this handler's trailing slots are mirror slots the loop granted, and so should be
     * hidden from the Curios screen. Counting the mirror stacks actually present - rather than what
     * the loop's contents imply - is the safe signal: a base slot never holds a mirror, so this can
     * only ever hide slots the loop added. The server places the mirrors in {@code setPage}'s HEAD
     * hook before this runs; on the client they arrive with Curios' stack sync and this converges a
     * tick or two after a curio change.
     */
    public static int hiddenSlotCount(top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler stacks) {
        int count = 0;
        for (int i = stacks.getSlots() - 1; i >= 0 && isMirror(stacks.getStackInSlot(i)); i--) {
            count++;
        }
        return count;
    }

    /** Sum of {@link #hiddenSlotCount} across every type - keeps the screen's panel sizing honest. */
    public static int totalHiddenSlots(ICuriosItemHandler handler) {
        int total = 0;
        for (ICurioStacksHandler sh : handler.getCurios().values()) {
            total += hiddenSlotCount(sh.getStacks());
        }
        return total;
    }

    private static void purgeStrayMirrors(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isMirror(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (isMirror(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    public static boolean isMirror(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LOOP_MANAGED.get(), false);
    }

    private static ItemStack mirrorCopy(ItemStack source) {
        ItemStack copy = source.copy();
        copy.set(ModDataComponents.LOOP_MANAGED.get(), true);
        return copy;
    }

    public static void syncIfServer(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
