package dev.watdafawx.hungryloop.curio;

import dev.watdafawx.hungryloop.component.LoopContents;
import dev.watdafawx.hungryloop.component.ModDataComponents;
import dev.watdafawx.hungryloop.item.HungryLoopItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Contents past a loop's capacity are handed back to the player, never kept or deleted. Capacity can
 * drop under items (a lowered slot cap in the config, loot or commands writing past it), and any
 * future way of losing slots goes through the same path: the items always come out, into the
 * inventory, or at the player's feet if it is full.
 */
public final class LoopOverflow {

    private LoopOverflow() {}

    /** Splits everything past capacity off the loop (mutating it) and returns the non-empty stacks. */
    public static List<ItemStack> takeOverflow(ItemStack loop) {
        if (!(loop.getItem() instanceof HungryLoopItem)) return List.of();
        LoopContents data = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
        int capacity = data.totalSlots();
        if (data.items().size() <= capacity) return List.of();

        List<ItemStack> kept = new ArrayList<>(data.items().subList(0, capacity));
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : data.items().subList(capacity, data.items().size())) if (!s.isEmpty()) out.add(s.copy());
        loop.set(ModDataComponents.LOOP_CONTENTS.get(), data.withItems(kept));
        return out;
    }

    /** Ejects {@code loop}'s overflow to {@code player}; true if anything came out. */
    public static boolean eject(ServerPlayer player, ItemStack loop) {
        List<ItemStack> out = takeOverflow(loop);
        out.forEach(s -> player.getInventory().placeItemBackInInventory(s));
        return !out.isEmpty();
    }
}
