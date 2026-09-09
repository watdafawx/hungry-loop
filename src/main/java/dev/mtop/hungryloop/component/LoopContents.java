package dev.mtop.hungryloop.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mtop.hungryloop.Config;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The entire state of a Hungry Loop, stored as a data component on the stack.
 *
 * @param baseSlots  slots the loop was forged with - 4 normally, up to 8 for a shiny loot loop.
 * @param bonusSlots slots bought with experience.
 * @param capBonus   extra cap earned by feeding Nether Stars ({@code slotsPerStar} each). The actual
 *                   cap is {@code min(typeMax + capBonus, absoluteMax)} with all three from config,
 *                   read live so a config change takes effect on existing loops.
 * @param shiny      loot-forged variant (visual + higher base slot count + higher default cap).
 * @param storedXp   experience points banked toward the next slot.
 * @param items      contained curios, one per occupied sub-slot, in slot order.
 * @param selected   passthrough selection: {@code -1} = the loop acts as itself; {@code >= 0}
 *                   indexes {@link #items}. Shift-scroll cycles it.
 */
public record LoopContents(int baseSlots, int bonusSlots, int capBonus, boolean shiny, int storedXp,
                           List<ItemStack> items, int selected) {

    public static final int MIN_BASE_SLOTS = 4;
    public static final int MAX_SHINY_BASE_SLOTS = 8;

    public static final int LOOP_MODE = -1;

    public static final LoopContents DEFAULT =
            new LoopContents(MIN_BASE_SLOTS, 0, 0, false, 0, List.of(), LOOP_MODE);

    public static final Codec<LoopContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(MIN_BASE_SLOTS, MAX_SHINY_BASE_SLOTS).fieldOf("base_slots").forGetter(LoopContents::baseSlots),
            Codec.intRange(0, 64).optionalFieldOf("bonus_slots", 0).forGetter(LoopContents::bonusSlots),
            Codec.intRange(0, 64).optionalFieldOf("cap_bonus", 0).forGetter(LoopContents::capBonus),
            Codec.BOOL.optionalFieldOf("shiny", false).forGetter(LoopContents::shiny),
            Codec.INT.optionalFieldOf("stored_xp", 0).forGetter(LoopContents::storedXp),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(LoopContents::items),
            Codec.INT.optionalFieldOf("selected", LOOP_MODE).forGetter(LoopContents::selected)
    ).apply(i, LoopContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoopContents> STREAM_CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.baseSlots);
                buf.writeVarInt(v.bonusSlots);
                buf.writeVarInt(v.capBonus);
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

    /** Live from config: {@code min(typeMax + capBonus, absoluteMax)}. */
    public int slotCap() {
        return Config.slotCap(shiny, capBonus);
    }

    public int totalSlots() {
        return Math.min(baseSlots + bonusSlots, slotCap());
    }

    public boolean canGrow() {
        return totalSlots() < slotCap();
    }

    public boolean canRaiseCap() {
        return slotCap() < Config.absoluteMaxSlots();
    }

    public int nextSlotCost() {
        return slotCost(baseSlots, bonusSlots);
    }

    public static int slotCost(int baseSlots, int bonusSlots) {
        int have = baseSlots + bonusSlots;
        return (int) Math.round(5 * Math.pow(1.7, have - MIN_BASE_SLOTS)) + 3 * (baseSlots - MIN_BASE_SLOTS);
    }

    /** Cumulative XP levels to grow from the current size to a given total slot count. */
    public int costToReach(int targetTotal) {
        int base = baseSlots;
        int bonus = bonusSlots;
        int sum = -storedXp;
        while (base + bonus < Math.min(targetTotal, slotCap())) {
            sum += slotCost(base, bonus);
            bonus++;
        }
        return Math.max(0, sum);
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
        int cap = slotCap();

        while (base + bonus < cap) {
            int cost = slotCost(base, bonus);
            if (banked < cost) break;
            banked -= cost;
            bonus++;
        }
        return new LoopContents(base, bonus, capBonus, shiny, banked, items, selected);
    }

    public LoopContents withCapBonus(int newCapBonus) {
        return new LoopContents(baseSlots, bonusSlots, Math.max(0, newCapBonus),
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
        return new LoopContents(baseSlots, bonusSlots, capBonus, shiny, storedXp, trimmed, newSelected);
    }

    public LoopContents withSelected(int newSelected) {
        return new LoopContents(baseSlots, bonusSlots, capBonus, shiny, storedXp, items, newSelected);
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
