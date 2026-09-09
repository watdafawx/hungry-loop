package dev.mtop.hungryloop.curio;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Resolves which Curios slot type a contained item should occupy while it's in the loop.
 *
 * <p>The loop doesn't fake effects - it grants the wearer a real (hidden) Curios slot of this type
 * per contained item and mirrors the item into it ({@code LoopSlotMirror}), so every mod that
 * scans the Curios inventory sees it equipped in its own slot.
 */
public final class ContentForwarding {

    /** The generic catch-all slot type; every real slot type is preferred over it. */
    public static final String GENERIC_SLOT = "curio";

    private ContentForwarding() {}

    public static String resolveSlot(ItemStack stack, LivingEntity wearer) {
        var slots = CuriosApi.getItemStackSlots(stack, wearer).keySet();

        if (slots.isEmpty()) return GENERIC_SLOT;

        for (String id : slots) {
            if (!id.equals(GENERIC_SLOT)) return id;
        }
        return slots.iterator().next();
    }
}
