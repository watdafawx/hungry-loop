package dev.mtop.hungryloop.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The entire state of a Hungry Loop, stored as a data component on the stack.
 *
 * @param baseSlots  slots the loop was forged with - 4 normally, 4-8 for a shiny loot loop.
 * @param bonusSlots slots bought with experience.
 * @param shiny      loot-forged variant (visual + higher base slot count).
 * @param storedXp   experience points banked toward the next slot.
 * @param items      contained curios, one per occupied sub-slot, in slot order.
 * @param selected   passthrough selection: {@code -1} = the loop acts as itself (right-click opens
 *                   its menu); {@code >= 0} indexes {@link #items} and the loop forwards use/interact
 *                   to that item, for e.g. triggering a contained relic. Shift-scroll cycles it.
 */
public record LoopContents(int baseSlots, int bonusSlots, boolean shiny, int storedXp,
                           List<ItemStack> items, int selected) {

    public static final int MIN_BASE_SLOTS = 4;
    public static final int MAX_SHINY_BASE_SLOTS = 8;
    public static final int MAX_TOTAL_SLOTS = 12;

    public static final int LOOP_MODE = -1;

    public static final LoopContents DEFAULT =
            new LoopContents(MIN_BASE_SLOTS, 0, false, 0, List.of(), LOOP_MODE);

    public static final Codec<LoopContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(MIN_BASE_SLOTS, MAX_SHINY_BASE_SLOTS).fieldOf("base_slots").forGetter(LoopContents::baseSlots),
            Codec.intRange(0, MAX_TOTAL_SLOTS).optionalFieldOf("bonus_slots", 0).forGetter(LoopContents::bonusSlots),
            Codec.BOOL.optionalFieldOf("shiny", false).forGetter(LoopContents::shiny),
            Codec.INT.optionalFieldOf("stored_xp", 0).forGetter(LoopContents::storedXp),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(LoopContents::items),
            Codec.INT.optionalFieldOf("selected", LOOP_MODE).forGetter(LoopContents::selected)
    ).apply(i, LoopContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoopContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LoopContents::baseSlots,
            ByteBufCodecs.VAR_INT, LoopContents::bonusSlots,
            ByteBufCodecs.BOOL, LoopContents::shiny,
            ByteBufCodecs.VAR_INT, LoopContents::storedXp,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, LoopContents::items,
            ByteBufCodecs.VAR_INT, LoopContents::selected,
            LoopContents::new);

    public int totalSlots() {
        return Math.min(baseSlots + bonusSlots, MAX_TOTAL_SLOTS);
    }

    public boolean canGrow() {
        return totalSlots() < MAX_TOTAL_SLOTS;
    }

    /**
     * XP levels to add the next slot. Rises steeply with the slots you already have, plus a flat
     * surcharge per starting slot above the minimum - so a 4-slot loop and an 8-slot shiny converge
     * on a similar total investment to reach the same size.
     */
    public int nextSlotCost() {
        return slotCost(baseSlots, bonusSlots);
    }

    public static int slotCost(int baseSlots, int bonusSlots) {
        int have = baseSlots + bonusSlots;
        return (int) Math.round(5 * Math.pow(1.7, have - MIN_BASE_SLOTS)) + 3 * (baseSlots - MIN_BASE_SLOTS);
    }

    /** Progress toward the next slot, 0..1. */
    public float growthProgress() {
        return canGrow() ? Math.min(1f, (float) storedXp / nextSlotCost()) : 1f;
    }

    /**
     * Bank {@code levels} of experience and spend it on as many new slots as it covers.
     *
     * @return the fed loop, or {@code this} unchanged if nothing could be done
     */
    public LoopContents feed(int levels) {
        if (!canGrow() || levels <= 0) return this;

        int banked = storedXp + levels;
        int base = baseSlots;
        int bonus = bonusSlots;

        while (base + bonus < MAX_TOTAL_SLOTS) {
            int cost = slotCost(base, bonus);
            if (banked < cost) break;
            banked -= cost;
            bonus++;
        }
        return new LoopContents(base, bonus, shiny, banked, items, selected);
    }

    public boolean loopMode() {
        return selected < 0 || selectedStack().isEmpty();
    }

    /** The item the loop currently forwards interactions to, or EMPTY in loop mode. */
    public ItemStack selectedStack() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : ItemStack.EMPTY;
    }

    public NonNullList<ItemStack> toSlotList() {
        NonNullList<ItemStack> list = NonNullList.withSize(totalSlots(), ItemStack.EMPTY);
        for (int slot = 0; slot < Math.min(items.size(), list.size()); slot++) {
            list.set(slot, items.get(slot));
        }
        return list;
    }

    public LoopContents withItems(List<ItemStack> newItems) {
        int lastUsed = -1;
        for (int slot = 0; slot < newItems.size(); slot++) {
            if (!newItems.get(slot).isEmpty()) lastUsed = slot;
        }
        List<ItemStack> trimmed = List.copyOf(newItems.subList(0, lastUsed + 1));
        int newSelected = selected >= trimmed.size() ? LOOP_MODE : selected;
        return new LoopContents(baseSlots, bonusSlots, shiny, storedXp, trimmed, newSelected);
    }

    public LoopContents withSelected(int newSelected) {
        return new LoopContents(baseSlots, bonusSlots, shiny, storedXp, items, newSelected);
    }

    /** Cycle the selection by {@code direction} (+1 / -1) through: loop mode, then each non-empty item. */
    public LoopContents cycleSelected(int direction) {
        // build the ordered list of stops: -1 (loop), then indices of non-empty items
        java.util.List<Integer> stops = new java.util.ArrayList<>();
        stops.add(LOOP_MODE);
        for (int idx = 0; idx < items.size(); idx++) {
            if (!items.get(idx).isEmpty()) stops.add(idx);
        }
        int here = stops.indexOf(selected >= 0 && selected < items.size() && !items.get(selected).isEmpty() ? selected : LOOP_MODE);
        int next = Math.floorMod(here + Integer.signum(direction), stops.size());
        return withSelected(stops.get(next));
    }
}
