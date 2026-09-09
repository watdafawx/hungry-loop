package dev.mtop.hungryloop.curio;

import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.item.HungryLoopItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncRender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private static final Map<UUID, Integer> settleUntilTick = new HashMap<>();
    private static final int SETTLE_TICKS = 5;

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

        Map<String, List<Want>> wanted = new LinkedHashMap<>();
        for (SlotResult worn : handler.findCurios(s -> s.getItem() instanceof HungryLoopItem && !isMirror(s))) {
            LoopContents data = worn.stack()
                    .getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
            boolean loopVisible = data.selfRenders() && slotRenders(handler,
                    worn.slotContext().identifier(), worn.slotContext().index());

            List<ItemStack> items = data.items();
            for (int i = 0; i < items.size(); i++) {
                ItemStack contained = items.get(i);
                if (contained.isEmpty()) continue;
                wanted.computeIfAbsent(ContentForwarding.resolveSlot(contained, player), k -> new ArrayList<>())
                        .add(new Want(contained, loopVisible && data.rendersItem(i)));
            }
        }

        boolean stillSettling = settling;
        handler.getCurios().forEach((type, stacksHandler) ->
                mirrorInto(player, type, stacksHandler, wanted.getOrDefault(type, List.of()), stillSettling));
    }

    private static boolean slotRenders(ICuriosItemHandler handler, String type, int index) {
        return handler.getStacksHandler(type)
                .map(sh -> index >= sh.getRenders().size() || sh.getRenders().get(index))
                .orElse(true);
    }

    private static void mirrorInto(ServerPlayer player, String type, ICurioStacksHandler stacksHandler,
                                   List<Want> desired, boolean settling) {
        var stacks = stacksHandler.getStacks();
        int total = stacks.getSlots();

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

        List<Want> needed = new ArrayList<>(desired.size());
        for (Want w : desired) {
            needed.add(new Want(mirrorCopy(w.stack()), w.render()));
        }

        // Pass 1: keep a slot whose mirror is still wanted; make its render flag match.
        boolean[] keep = new boolean[ourSlots.size()];
        for (int k = 0; k < ourSlots.size(); k++) {
            int idx = ourSlots.get(k);
            ItemStack cur = stacks.getStackInSlot(idx);
            if (!isMirror(cur)) continue;
            for (Iterator<Want> it = needed.iterator(); it.hasNext(); ) {
                Want w = it.next();
                if (ItemStack.matches(cur, w.stack())) {
                    it.remove();
                    keep[k] = true;
                    setRender(player, stacksHandler, type, idx, w.render());
                    break;
                }
            }
        }

        // Pass 2: fill leftovers, clear no-longer-wanted mirrors.
        for (int k = 0; k < ourSlots.size(); k++) {
            if (keep[k]) continue;
            int idx = ourSlots.get(k);
            ItemStack cur = stacks.getStackInSlot(idx);

            if (!needed.isEmpty()) {
                if (settling && cur.isEmpty() && !isMirror(cur)) continue;
                Want w = needed.remove(0);
                stacks.setStackInSlot(idx, w.stack());
                setRender(player, stacksHandler, type, idx, w.render());
            } else if (isMirror(cur)) {
                stacks.setStackInSlot(idx, ItemStack.EMPTY);
            }
        }
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

    public static int grantedCount(Player player, String type) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return 0;
        int count = 0;
        for (SlotResult worn : handler.findCurios(s -> s.getItem() instanceof HungryLoopItem && !isMirror(s))) {
            LoopContents data = worn.stack()
                    .getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
            for (ItemStack contained : data.items()) {
                if (!contained.isEmpty() && ContentForwarding.resolveSlot(contained, player).equals(type)) {
                    count++;
                }
            }
        }
        return count;
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
