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
 * @param baseSlots  slots the loop was forged with - 4 normally, 4-8 for a shiny loot loop. Fixed for
 *                   the life of the item; drives the cost of every <em>added</em> slot.
 * @param bonusSlots slots bought with experience.
 * @param shiny      loot-forged variant (visual + higher base slot count).
 * @param storedXp   experience points banked toward the next slot.
 * @param items      contained curios, one per occupied sub-slot, in slot order. May be shorter than
 *                   {@link #totalSlots()} (trailing slots are empty) and never longer once normalised.
 */
public record LoopContents(int baseSlots, int bonusSlots, boolean shiny, int storedXp, List<ItemStack> items) {

    public static final int MIN_BASE_SLOTS = 4;
    public static final int MAX_SHINY_BASE_SLOTS = 8;
    public static final int MAX_TOTAL_SLOTS = 12;

    /** What a freshly-registered, non-shiny loop holds. */
    public static final LoopContents DEFAULT = new LoopContents(MIN_BASE_SLOTS, 0, false, 0, List.of());

    public static final Codec<LoopContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(MIN_BASE_SLOTS, MAX_SHINY_BASE_SLOTS).fieldOf("base_slots").forGetter(LoopContents::baseSlots),
            Codec.intRange(0, MAX_TOTAL_SLOTS).optionalFieldOf("bonus_slots", 0).forGetter(LoopContents::bonusSlots),
            Codec.BOOL.optionalFieldOf("shiny", false).forGetter(LoopContents::shiny),
            Codec.INT.optionalFieldOf("stored_xp", 0).forGetter(LoopContents::storedXp),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(LoopContents::items)
    ).apply(i, LoopContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoopContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LoopContents::baseSlots,
            ByteBufCodecs.VAR_INT, LoopContents::bonusSlots,
            ByteBufCodecs.BOOL, LoopContents::shiny,
            ByteBufCodecs.VAR_INT, LoopContents::storedXp,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, LoopContents::items,
            LoopContents::new);

    /** Slots available right now, clamped to the hard cap. */
    public int totalSlots() {
        return Math.min(baseSlots + bonusSlots, MAX_TOTAL_SLOTS);
    }

    /** Contents as a fixed-size, mutable list padded with {@link ItemStack#EMPTY} - what a menu edits. */
    public NonNullList<ItemStack> toSlotList() {
        NonNullList<ItemStack> list = NonNullList.withSize(totalSlots(), ItemStack.EMPTY);
        for (int slot = 0; slot < Math.min(items.size(), list.size()); slot++) {
            list.set(slot, items.get(slot));
        }
        return list;
    }

    /** A copy with the contained items replaced (trailing empties trimmed). */
    public LoopContents withItems(List<ItemStack> newItems) {
        int lastUsed = -1;
        for (int slot = 0; slot < newItems.size(); slot++) {
            if (!newItems.get(slot).isEmpty()) lastUsed = slot;
        }
        return new LoopContents(baseSlots, bonusSlots, shiny, storedXp,
                List.copyOf(newItems.subList(0, lastUsed + 1)));
    }
}
