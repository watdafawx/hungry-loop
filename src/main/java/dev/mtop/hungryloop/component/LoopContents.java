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
import java.util.Objects;

/**
 * The entire state of a Hungry Loop, stored as a data component on the stack.
 *
 * @param baseSlots   slots the loop was forged with - 4 normally, up to 8 for a shiny loot loop.
 * @param bonusSlots  slots bought with experience.
 * @param capBonus    extra cap earned by feeding Nether Stars. Actual cap is
 *                    {@code min(typeMax + capBonus, absoluteMax)}, all from config, read live.
 * @param shiny       loot-forged variant.
 * @param storedXp    experience banked toward the next slot.
 * @param items       contained curios, one per occupied sub-slot, in slot order.
 * @param selected    passthrough selection: {@code -1} = acts as itself; {@code >= 0} indexes items.
 * @param renderMask  bit {@code i} set = contained item {@code i}'s cosmetic renders on the body.
 * @param selfRenders master toggle - {@code false} hides every contained cosmetic at once.
 */
public record LoopContents(int baseSlots, int bonusSlots, int capBonus, boolean shiny, int storedXp,
                           List<ItemStack> items, int selected, long renderMask, boolean selfRenders) {

    public static final int MIN_BASE_SLOTS = 4;
    public static final int MAX_SHINY_BASE_SLOTS = 8;
    public static final int LOOP_MODE = -1;
    public static final long ALL_RENDER = -1L;

    public static final LoopContents DEFAULT =
            new LoopContents(MIN_BASE_SLOTS, 0, 0, false, 0, List.of(), LOOP_MODE, ALL_RENDER, true);

    public static final Codec<LoopContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(MIN_BASE_SLOTS, MAX_SHINY_BASE_SLOTS).fieldOf("base_slots").forGetter(LoopContents::baseSlots),
            Codec.intRange(0, 64).optionalFieldOf("bonus_slots", 0).forGetter(LoopContents::bonusSlots),
            Codec.intRange(0, 64).optionalFieldOf("cap_bonus", 0).forGetter(LoopContents::capBonus),
            Codec.BOOL.optionalFieldOf("shiny", false).forGetter(LoopContents::shiny),
            Codec.INT.optionalFieldOf("stored_xp", 0).forGetter(LoopContents::storedXp),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(LoopContents::items),
            Codec.INT.optionalFieldOf("selected", LOOP_MODE).forGetter(LoopContents::selected),
            Codec.LONG.optionalFieldOf("render_mask", ALL_RENDER).forGetter(LoopContents::renderMask),
            Codec.BOOL.optionalFieldOf("self_renders", true).forGetter(LoopContents::selfRenders)
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
                buf.writeLong(v.renderMask);
                buf.writeBoolean(v.selfRenders);
            },
            buf -> new LoopContents(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf),
                    buf.readVarInt(),
                    buf.readLong(),
                    buf.readBoolean()));

    // --- slots -------------------------------------------------------------------------------

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
        return new LoopContents(base, bonus, capBonus, shiny, banked, items, selected, renderMask, selfRenders);
    }

    public LoopContents withCapBonus(int newCapBonus) {
        return new LoopContents(baseSlots, bonusSlots, Math.max(0, newCapBonus), shiny, storedXp,
                items, selected, renderMask, selfRenders);
    }

    // --- passthrough -----------------------------------------------------------------------

    public boolean loopMode() {
        return selected < 0 || selectedStack().isEmpty();
    }

    public ItemStack selectedStack() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : ItemStack.EMPTY;
    }

    public LoopContents withSelected(int newSelected) {
        return new LoopContents(baseSlots, bonusSlots, capBonus, shiny, storedXp, items, newSelected,
                renderMask, selfRenders);
    }

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

    // --- render toggles --------------------------------------------------------------------

    public boolean rendersItem(int index) {
        return selfRenders && index >= 0 && index < 64 && (renderMask & (1L << index)) != 0;
    }

    public LoopContents toggleItemRender(int index) {
        if (index < 0 || index >= 64) return this;
        return new LoopContents(baseSlots, bonusSlots, capBonus, shiny, storedXp, items, selected,
                renderMask ^ (1L << index), selfRenders);
    }

    public LoopContents toggleSelfRender() {
        return new LoopContents(baseSlots, bonusSlots, capBonus, shiny, storedXp, items, selected,
                renderMask, !selfRenders);
    }

    // --- contents --------------------------------------------------------------------------

    public NonNullList<ItemStack> toSlotList() {
        NonNullList<ItemStack> list = NonNullList.withSize(totalSlots(), ItemStack.EMPTY);
        for (int slot = 0; slot < Math.min(items.size(), list.size()); slot++) {
            list.set(slot, items.get(slot));
        }
        return list;
    }

    // --- equality ---------------------------------------------------------------------------

    /*
     * ItemStack has no value equals(), so the record's generated equals() would compare the items
     * list by reference. That matters a lot here: Curios' tick loop does
     * {@code ItemStack.matches(loop, previousLoopCopy)} every tick, and a copied component means a
     * new list of new ItemStack objects - so the loop would look changed every single tick, and
     * Curios would tear down and rebuild every slot it grants each tick. Compare the items by value.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoopContents other)) return false;
        if (baseSlots != other.baseSlots || bonusSlots != other.bonusSlots || capBonus != other.capBonus
                || shiny != other.shiny || storedXp != other.storedXp || selected != other.selected
                || renderMask != other.renderMask || selfRenders != other.selfRenders
                || items.size() != other.items.size()) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            if (!ItemStack.matches(items.get(i), other.items.get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(baseSlots, bonusSlots, capBonus, shiny, storedXp, selected, renderMask,
                selfRenders, items.size());
        for (ItemStack s : items) {
            h = 31 * h + ItemStack.hashItemAndComponents(s);
        }
        return h;
    }

    public LoopContents withItems(List<ItemStack> newItems) {
        int lastUsed = -1;
        for (int slot = 0; slot < newItems.size(); slot++) {
            if (!newItems.get(slot).isEmpty()) lastUsed = slot;
        }
        List<ItemStack> trimmed = List.copyOf(newItems.subList(0, lastUsed + 1));
        int newSelected = selected >= trimmed.size() ? LOOP_MODE : selected;
        return new LoopContents(baseSlots, bonusSlots, capBonus, shiny, storedXp, trimmed, newSelected,
                renderMask, selfRenders);
    }
}
