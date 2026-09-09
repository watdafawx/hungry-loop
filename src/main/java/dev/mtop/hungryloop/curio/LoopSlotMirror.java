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
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.event.SlotModifiersUpdatedEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the real Curios slots the loop grants ({@link HungryLoopCurio#getAttributeModifiers}) filled
 * with the loop's contents, as marked mirror copies. The loop's {@link LoopContents} component is the
 * single source of truth.
 *
 * <p>Reconciliation is <b>incremental and content-matched</b>: a mirror already holding an item the
 * loop still wants is left exactly where it is. Adding or removing one item never disturbs the other
 * mirrors, so an already-equipped relic's effect doesn't blink.
 *
 * <p>Mirrors are copies - if one ever leaks out (a slot shrink dropping it, say) the ItemEntity is
 * cancelled and any stray in the inventory is purged, so it just vanishes and gets recreated.
 */
@EventBusSubscriber(modid = HungryLoop.MODID)
public final class LoopSlotMirror {

    private static final int SYNC_INTERVAL = 3;

    /**
     * When a player's granted-slot count last changed. For a few ticks after, we don't write mirrors
     * into freshly-empty slots, so Curios has time to sync the new slot count to the client first
     * (otherwise the client gets a {@code sync_stack} for a slot it doesn't have yet).
     */
    private static final Map<UUID, Integer> settleUntilTick = new HashMap<>();
    private static final int SETTLE_TICKS = 5;

    private LoopSlotMirror() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % SYNC_INTERVAL == 0) {
            purgeStrayMirrors(player);
            sync(player, false);
        }
    }

    /** A mirror only ever belongs in a granted slot. Never let one exist as a dropped item. */
    @SubscribeEvent
    static void onItemEntitySpawn(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity itemEntity && isMirror(itemEntity.getItem())) {
            event.setCanceled(true);
        }
    }

    /** A loop came off (or swapped out): reconcile now so its mirrors are pulled before the shrink. */
    @SubscribeEvent
    static void onCurioChange(CurioChangeEvent event) {
        if (event.getFrom().getItem() instanceof HungryLoopItem
                && event.getEntity() instanceof ServerPlayer player) {
            sync(player, true);
        }
    }

    @SubscribeEvent
    static void onSlotModifiersUpdated(SlotModifiersUpdatedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            settleUntilTick.put(player.getUUID(), player.tickCount + SETTLE_TICKS);
        }
    }

    public static void sync(ServerPlayer player) {
        sync(player, true);
    }

    public static void sync(ServerPlayer player, boolean force) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return;

        boolean settling = !force
                && player.tickCount < settleUntilTick.getOrDefault(player.getUUID(), 0);

        Map<String, List<ItemStack>> wanted = new LinkedHashMap<>();
        for (ItemStack loop : wornLoops(handler)) {
            LoopContents data = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
            for (ItemStack contained : data.items()) {
                if (contained.isEmpty()) continue;
                wanted.computeIfAbsent(ContentForwarding.resolveSlot(contained, player), k -> new ArrayList<>())
                        .add(contained);
            }
        }

        boolean stillSettling = settling;
        handler.getCurios().forEach((type, stacksHandler) ->
                mirrorInto(stacksHandler.getStacks(), wanted.getOrDefault(type, List.of()), stillSettling));
    }

    /**
     * Reconcile the trailing (granted) slots of one type to hold exactly {@code desired}, keeping any
     * mirror that already holds a wanted item untouched.
     */
    private static void mirrorInto(IDynamicStackHandler stacks, List<ItemStack> desired, boolean settling) {
        int total = stacks.getSlots();

        // The trailing run of empty / mirror slots - everything after the wearer's real curios.
        List<Integer> ourSlots = new ArrayList<>();
        for (int i = total - 1; i >= 0; i--) {
            ItemStack s = stacks.getStackInSlot(i);
            if (s.isEmpty() || isMirror(s)) {
                ourSlots.add(i);
            } else {
                break;
            }
        }
        Collections.reverse(ourSlots);

        List<ItemStack> needed = new ArrayList<>(desired.size());
        for (ItemStack d : desired) {
            needed.add(mirrorCopy(d));
        }

        // Pass 1: a slot whose mirror is still wanted keeps it, untouched.
        boolean[] keep = new boolean[ourSlots.size()];
        for (int k = 0; k < ourSlots.size(); k++) {
            ItemStack cur = stacks.getStackInSlot(ourSlots.get(k));
            if (!isMirror(cur)) continue;
            for (Iterator<ItemStack> it = needed.iterator(); it.hasNext(); ) {
                if (ItemStack.matches(cur, it.next())) {
                    it.remove();
                    keep[k] = true;
                    break;
                }
            }
        }

        // Pass 2: fill the leftovers into free slots, clear the mirrors that are no longer wanted.
        for (int k = 0; k < ourSlots.size(); k++) {
            if (keep[k]) continue;
            int idx = ourSlots.get(k);
            ItemStack cur = stacks.getStackInSlot(idx);

            if (!needed.isEmpty()) {
                if (settling && cur.isEmpty() && !isMirror(cur)) continue; // let the new slot count sync first
                stacks.setStackInSlot(idx, needed.remove(0));
            } else if (isMirror(cur)) {
                stacks.setStackInSlot(idx, ItemStack.EMPTY);
            }
        }
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

    /**
     * How many slots of {@code type} the player's worn loops grant right now. The Curios screen hides
     * exactly this many trailing slots of the type, whether their mirrors have arrived yet or not.
     */
    public static int grantedCount(net.minecraft.world.entity.player.Player player, String type) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return 0;

        int count = 0;
        for (ItemStack loop : wornLoops(handler)) {
            LoopContents data = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
            for (ItemStack contained : data.items()) {
                if (!contained.isEmpty() && ContentForwarding.resolveSlot(contained, player).equals(type)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static List<ItemStack> wornLoops(ICuriosItemHandler handler) {
        List<ItemStack> loops = new ArrayList<>();
        handler.findCurios(stack -> stack.getItem() instanceof HungryLoopItem && !isMirror(stack))
                .forEach(result -> loops.add(result.stack()));
        return loops;
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
