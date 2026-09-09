package dev.mtop.hungryloop.curio;

import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.item.HungryLoopItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.event.SlotModifiersUpdatedEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the real Curios slots the loop grants ({@link HungryLoopCurio#getAttributeModifiers}) filled
 * with the loop's contents, as marked mirror copies.
 *
 * <p>Model: the loop's {@link LoopContents} component is the single source of truth. These mirrors
 * exist only so other mods see the items equipped; the player can't touch them (the Curios-screen
 * slot is made read-only by {@code mixin.CurioSlotMixin}), and they're cleared the moment the loop
 * comes off - before Curios shrinks the granted slots and would otherwise drop them on the floor.
 */
@EventBusSubscriber(modid = HungryLoop.MODID)
public final class LoopSlotMirror {

    private static final int SYNC_INTERVAL = 5;

    private LoopSlotMirror() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % SYNC_INTERVAL == 0) {
            sync(player);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearAllMirrors(player); // drop anything stale from a crash; the tick rebuilds it
        }
    }

    /** A loop was taken off (or swapped out): pull our mirrors before Curios shrinks the slots. */
    @SubscribeEvent
    static void onCurioChange(CurioChangeEvent event) {
        if (event.getFrom().getItem() instanceof HungryLoopItem
                && !(event.getTo().getItem() instanceof HungryLoopItem)
                && event.getEntity() instanceof ServerPlayer player) {
            clearAllMirrors(player);
        }
    }

    @SubscribeEvent
    static void onSlotModifiersUpdated(SlotModifiersUpdatedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Slot counts just changed. Clear then let the next tick refill against the new layout,
            // so a shrink never catches a mirror in a slot that's about to vanish.
            clearAllMirrors(player);
        }
    }

    /** Rebuild every player's mirrors to match their worn loops. Server side only. */
    public static void sync(ServerPlayer player) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return;

        // Desired: contained items of every worn loop, grouped by resolved slot type, in a stable order.
        Map<String, List<ItemStack>> wanted = new LinkedHashMap<>();
        for (ItemStack loop : wornLoops(handler)) {
            LoopContents data = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
            for (ItemStack contained : data.items()) {
                if (contained.isEmpty()) continue;
                wanted.computeIfAbsent(ContentForwarding.resolveSlot(contained, player), k -> new ArrayList<>())
                        .add(contained);
            }
        }

        // Clear mirrors in types we no longer want anything in.
        handler.getCurios().forEach((type, stacksHandler) -> {
            if (!wanted.containsKey(type)) {
                clearMirrors(stacksHandler.getStacks());
            }
        });

        wanted.forEach((type, items) -> handler.getStacksHandler(type).ifPresent(stacksHandler ->
                mirrorInto(stacksHandler.getStacks(), items)));
    }

    private static void mirrorInto(IDynamicStackHandler stacks, List<ItemStack> items) {
        int total = stacks.getSlots();

        // Our slots are the trailing run of empty / already-mirrored slots - the ones the loop's
        // SlotAttribute grant just added, sitting after the wearer's real slots of this type.
        List<Integer> ours = new ArrayList<>();
        for (int i = total - 1; i >= 0 && ours.size() < items.size(); i--) {
            ItemStack inSlot = stacks.getStackInSlot(i);
            if (inSlot.isEmpty() || isMirror(inSlot)) {
                ours.add(i);
            } else {
                break; // hit a genuine equipped curio - stop
            }
        }
        ours.sort(null);

        for (int i = 0; i < total; i++) {
            int fillIndex = ours.indexOf(i);
            ItemStack current = stacks.getStackInSlot(i);

            if (fillIndex >= 0 && fillIndex < items.size()) {
                ItemStack expected = mirrorCopy(items.get(fillIndex));
                if (!ItemStack.matches(current, expected)) {
                    stacks.setStackInSlot(i, expected);
                }
            } else if (isMirror(current)) {
                stacks.setStackInSlot(i, ItemStack.EMPTY); // stale mirror (contents shrank)
            }
        }
    }

    private static void clearAllMirrors(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.getCurios().values().forEach(h -> clearMirrors(h.getStacks())));
    }

    private static void clearMirrors(IDynamicStackHandler stacks) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (isMirror(stacks.getStackInSlot(i))) {
                stacks.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
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

    /** Convenience for callers that just have the entity. */
    public static void syncIfServer(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
