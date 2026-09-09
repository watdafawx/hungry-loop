package dev.mtop.hungryloop.curio;

import dev.mtop.hungryloop.component.LoopContents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;

import java.util.List;

/**
 * Turns "an item sitting in a loop sub-slot" into "an item Curios thinks is equipped in its own
 * native slot". Everything the loop forwards - ticks, equip/unequip, attribute modifiers - goes
 * through here so the synthesized {@link SlotContext} is built one way only.
 *
 * <p>The generic {@code "curio"} slot type is the catch-all Curios validates against; a contained
 * item that also fits a specific type ({@code "ring"}, {@code "necklace"}, ...) is forwarded as that
 * type instead, because that is what slot-gated affixes and abilities check.
 */
public final class ContentForwarding {

    private static final String GENERIC_SLOT = "curio";

    private ContentForwarding() {}

    /** The Curios slot type a contained stack should behave as while inside the loop. */
    public static String resolveSlot(ItemStack stack, LivingEntity wearer) {
        var slots = CuriosApi.getItemStackSlots(stack, wearer).keySet();

        if (slots.isEmpty()) return GENERIC_SLOT;

        for (String id : slots) {
            if (!id.equals(GENERIC_SLOT)) return id;
        }
        return slots.iterator().next();
    }

    /** A slot context for a contained item, carrying the loop's wearer but the sub-slot's identity. */
    public static SlotContext subContext(SlotContext loopContext, String slotType, int subIndex) {
        return new SlotContext(slotType, loopContext.entity(), subIndex, false, loopContext.visible());
    }

    @FunctionalInterface
    public interface PerContent {
        void accept(ICurio curio, SlotContext subContext, ItemStack stack, int subIndex);
    }

    /** Runs {@code action} for every non-empty contained stack that carries a curio capability. */
    public static void each(SlotContext loopContext, LoopContents contents, PerContent action) {
        List<ItemStack> items = contents.items();

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;

            var curio = CuriosApi.getCurio(stack);
            if (curio.isEmpty()) continue;

            String slotType = resolveSlot(stack, loopContext.entity());
            action.accept(curio.get(), subContext(loopContext, slotType, i), stack, i);
        }
    }
}
