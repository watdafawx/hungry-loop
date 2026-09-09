package dev.mtop.hungryloop.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The entire state of a Hungry Loop, stored as a data component on the stack.
 *
 * @param baseSlots  slots the loop was forged with - 4 normally, 4-8 for a shiny loot loop.
 * @param bonusSlots slots bought with experience.
 * @param slotCap    the most slots this loop can reach - {@link #DEFAULT_CAP} normally, raised by
 *                   feeding it Nether Stars (config: {@code slotsPerStar}, {@code absoluteMaxSlots}).
 * @param shiny      loot-forged variant (visual + higher base slot count).
 * @param storedXp   experience points banked toward the next slot.
 * @param items      contained curios, one per occupied sub-slot, in slot order.
 * @param selected   passthrough selection: {@code -1} = the loop acts as itself; {@code >= 0}
 *                   indexes {@link #items}. Shift-scroll cycles it.
 */
public record LoopContents(int baseSlots, int bonusSlots, int slotCap, boolean shiny, int storedXp,
                           List<ItemStack> items, int selected) {

    public static final int MIN_BASE_SLOTS = 4;
    public static final int MAX_SHINY_BASE_SLOTS = 8;
    public static final int DEFAULT_CAP = 12;

    public static final int LOOP_MODE = -1;

    public static final LoopContents DEFAULT =
            new LoopContents(MIN_BASE_SLOTS, 0, DEFAULT_CAP, false, 0, List.of(), LOOP_MODE);

    public static final Codec<LoopContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(MIN_BASE_SLOTS, MAX_SHINY_BASE_SLOTS).fieldOf("base_slots").forGetter(LoopContents::baseSlots),
            Codec.intRange(0, 64).optionalFieldOf("bonus_slots", 0).forGetter(LoopContents::bonusSlots),
            Codec.intRange(MIN_BASE_SLOTS, 64).optionalFieldOf("slot_cap", DEFAULT_CAP).forGetter(LoopContents::slotCap),
            Codec.BOOL.optionalFieldOf("shiny", false).forGetter(LoopContents::shiny),
            Codec.INT.optionalFieldOf("stored_xp", 0).forGetter(LoopContents::storedXp),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(LoopContents::items),
            Codec.INT.optionalFieldOf("selected", LOOP_MODE).forGetter(LoopContents::selected)
    ).apply(i, LoopContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoopContents> STREAM_CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.baseSlots);
                buf.writeVarInt(v.bonusSlots);
                buf.writeVarInt(v.slotCap);
                buf.writeBoolean(v.shiny);
                buf.writeVarInt(v.storedXp);
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, v.items);
                buf.writeVarInt(v.selected);
            },
            buf -> new LoopContents(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf),
                    buf.readVarInt()));

    public int totalSlots() {
        return Math.min(baseSlots + bonusSlots, slotCap);
    }

    public boolean canGrow() {
        return totalSlots() < slotCap;
    }

    public boolean canRaiseCap(int absoluteMax) {
        return slotCap < absoluteMax;
    }

    public int nextSlotCost() {
        return slotCost(baseSlots, bonusSlots);
    }

    public static int slotCost(int baseSlots, int bonusSlots) {
        int have = baseSlots + bonusSlots;
        return (int) Math.round(5 * Math.pow(1.7, have - MIN_BASE_SLOTS)) + 3 * (baseSlots - MIN_BASE_SLOTS);
    }

    public float growthProgress() {
        return canGrow() ? Math.min(1f, (float) storedXp / nextSlotCost()) : 1f;
    }

    /** Bank {@code levels} of XP and spend it on as many new slots as it covers. */
    public LoopContents feed(int levels) {
        if (!canGrow() || levels <= 0) return this;

        int banked = storedXp + levels;
        int base = baseSlots;
        int bonus = bonusSlots;

        while (base + bonus < slotCap) {
            int cost = slotCost(base, bonus);
            if (banked < cost) break;
            banked -= cost;
            bonus++;
        }
        return new LoopContents(base, bonus, slotCap, shiny, banked, items, selected);
    }

    /** Raise the cap by {@code by}, never past {@code absoluteMax}. */
    public LoopContents raiseCap(int by, int absoluteMax) {
        return new LoopContents(baseSlots, bonusSlots, Math.min(slotCap + by, absoluteMax),
                shiny, storedXp, items, selected);
    }

    public boolean loopMode() {
        return selected < 0 || selectedStack().isEmpty();
    }

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
        return new LoopContents(baseSlots, bonusSlots, slotCap, shiny, storedXp, trimmed, newSelected);
    }

    public LoopContents withSelected(int newSelected) {
        return new LoopContents(baseSlots, bonusSlots, slotCap, shiny, storedXp, items, newSelected);
    }

    /** Cycle the selection by {@code direction} through: loop mode, then each non-empty item. */
    public LoopContents cycleSelected(int direction) {
        List<Integer> stops = new ArrayList<>();
        stops.add(LOOP_MODE);
        for (int idx = 0; idx < items.size(); idx++) {
            if (!items.get(idx).isEmpty()) stops.add(idx);
        }
        int current = selected >= 0 && selected < items.size() && !items.get(selected).isEmpty()
                ? selected : LOOP_MODE;
        int here = stops.indexOf(current);
        int next = Math.floorMod(here + Integer.signum(direction), stops.size());
        return withSelected(stops.get(next));
    }
}
