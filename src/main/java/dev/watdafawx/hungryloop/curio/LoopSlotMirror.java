package dev.watdafawx.hungryloop.curio;

import dev.watdafawx.hungryloop.HungryLoop;
import dev.watdafawx.hungryloop.component.LoopContents;
import dev.watdafawx.hungryloop.component.ModDataComponents;
import dev.watdafawx.hungryloop.integration.accessories.AccessoriesSync;
import dev.watdafawx.hungryloop.item.HungryLoopItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
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
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncCurios;
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
 *
 * <p><b>The granted slots are hidden purely by {@code CurioSlotMixin.isActive()}</b> - never by
 * changing how many slots the Curios container builds. The container's slot list has to be identical
 * on both sides, because vanilla syncs stacks into it by raw index; trimming it by the mirror count
 * (which lags on the client) made the two lists different lengths mid-equip and dropped the loop's
 * own stack into whatever slot happened to sit at that index client-side - a ghost copy that then
 * stuck, since the server never resends a slot it considers settled.
 */
@EventBusSubscriber(modid = HungryLoop.MODID)
public final class LoopSlotMirror {

    private static final int SYNC_INTERVAL = 3;

    private static final boolean ACCESSORIES = ModList.get().isLoaded("accessories");

    /**
     * Where {@code CuriosPanelLayoutMixin} parks a hidden (mirror-holding) slot: far enough off-panel
     * that it can't be drawn or hovered, without overflowing. {@code client.SelfTest} also reads it: a
     * slot holding a mirror that isn't parked (or a parked slot that doesn't) is a stale layout.
     */
    public static final int PARKED = -30000;

    /**
     * Players whose mirrors are being reconciled right now. Writing a mirror stack makes Curios
     * recompute slot modifiers, which fires {@link SlotModifiersUpdatedEvent} again mid-sync; the
     * open Curios screen's {@code setPage} does the same. Without this guard those re-entrant calls
     * storm each other and the granted slots flicker. Any reconcile we skip is picked up by the
     * next tick sync.
     */
    private static final Set<UUID> RECONCILING = ConcurrentHashMap.newKeySet();

    /**
     * Players whose last loop left a slot, pending a next-tick re-check. A move looks exactly like an
     * unequip for one tick (leave fires before enter), so the purge waits to see which it was.
     */
    private static final Set<UUID> PURGE_PENDING = ConcurrentHashMap.newKeySet();

    private record Want(ItemStack stack, boolean render) {}

    private LoopSlotMirror() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (PURGE_PENDING.remove(player.getUUID())) resolvePendingPurge(player);

        if (player.tickCount % SYNC_INTERVAL == 0) {
            purgeStrayMirrors(player);
            sync(player, false);
        }
    }

    /** A loop left a slot last tick: purge only if it really is gone, rather than mid-move. */
    private static void resolvePendingPurge(ServerPlayer player) {
        boolean anyLeft = CuriosApi.getCuriosInventory(player)
                .map(h -> !h.findCurios(s -> s.getItem() instanceof HungryLoopItem && !isMirror(s)).isEmpty())
                .orElse(false);
        if (anyLeft) {
            sync(player, true); // it was a move - reconcile against where the loop landed
        } else if (RECONCILING.add(player.getUUID())) {
            try {
                purgeAllMirrors(player);
            } finally {
                RECONCILING.remove(player.getUUID());
            }
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
            } else {
                // Don't purge yet. Moving the loop between slots fires the leave event before the
                // enter event, so "no loop equipped" is also what the middle of a move looks like -
                // purging here tore down every mirror and rebuilt it a tick later, churning the whole
                // curio inventory for what the player saw as dragging one item one slot across.
                // Defer to the next tick and re-check: a real unequip still has no loop by then, a
                // move does.
                PURGE_PENDING.add(player.getUUID());
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
            // The loop's own render state is the component's, not the slot's. Curios keeps render
            // flags in a per-slot-index list, so reading the flag here meant the setting belonged to
            // whichever slot the loop happened to sit in - move the loop and it picked up the new
            // slot's default (visible), silently switching every contained cosmetic back on. Push the
            // component into the slot instead, so the setting travels with the item. The loop slot
            // type sets "render_toggle": false so Curios doesn't offer a second, conflicting button
            // for it; the loop screen's own eye is the UI.
            boolean loopVisible = data.selfRenders();
            handler.getStacksHandler(worn.slotContext().identifier()).ifPresent(sh ->
                    setRender(player, sh, worn.slotContext().identifier(),
                            worn.slotContext().index(), data.selfRenders()));

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

        if (changed[0]) {
            if (player.containerMenu instanceof ICuriosMenu menu) {
                menu.resetSlots();
            }
            // Push the whole curio inventory, not the per-slot deltas Curios would otherwise trickle
            // out. SPacketSyncStack addresses a slot by raw index, while SPacketSyncModifiers resizes
            // the very list those indices address - and the loop resizes things constantly, so the
            // two race: a slot's stack could arrive before the slot existed, or land in an index that
            // meant something else by then, leaving a ghost the server never corrects.
            // SPacketSyncCurios carries every handler's sizes and stacks together and rebuilds the
            // client's slot list on arrival, so there is no window to race. This one packet replaced
            // three client-side patches (a deferred/replayed stack sync, a per-packet screen rebuild,
            // a rebuild gate); client.SelfTest's equip-while-open and move scenarios fail without it.
            // Only on an actual layout change, so this is rare - not the per-tick cooldown churn.
            // With Accessories' Curios compat layer that packet is dropped on the client; the same
            // all-at-once push goes through Accessories' own full sync instead.
            if (ACCESSORIES) {
                AccessoriesSync.fullSync(player);
            } else {
                PacketDistributor.sendToPlayer(player, new SPacketSyncCurios(player.getId(), handler.getCurios()));
            }
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

    /**
     * Mirrors {@code desired} into this type's <em>trailing</em> slots and nowhere else, so the
     * player's own curios keep their slot indices and the loop's additions stay grouped at the end.
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
