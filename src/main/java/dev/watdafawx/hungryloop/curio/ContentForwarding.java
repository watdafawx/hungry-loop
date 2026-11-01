package dev.watdafawx.hungryloop.curio;

import dev.watdafawx.hungryloop.HungryLoop;
import dev.watdafawx.hungryloop.integration.apotheosis.ApothSlots;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

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

    private static final boolean APOTHEOSIS = ModList.get().isLoaded(HungryLoop.APOTHEOSIS);

    public static String resolveSlot(ItemStack stack, LivingEntity wearer) {
        // Sorted so the choice is deterministic: getItemStackSlots' key order isn't stable, and if
        // getAttributeModifiers and LoopSlotMirror pick different types for the same item we end up
        // granting one slot type and mirroring into another.
        List<String> slots = new ArrayList<>(CuriosApi.getItemStackSlots(stack, wearer).keySet());
        // Wildcard curios are valid in the loop's own slot type too; never mirror into it.
        slots.remove(HungryLoopCurio.LOOP_SLOT);
        if (slots.isEmpty()) return GENERIC_SLOT;
        slots.sort(null);

        // An affixed item only works from the slot type its Apotheosis loot category reads - for a
        // wildcard tagged curios:curio that is "curio", which the preference below would otherwise
        // skip in favour of whichever real type sorts first, silently killing every affix on it.
        if (APOTHEOSIS) {
            String affixSlot = ApothSlots.affixSlotType(stack).orElse(null);
            if (affixSlot != null && slots.contains(affixSlot)) return affixSlot;
        }

        for (String id : slots) {
            if (!id.equals(GENERIC_SLOT)) return id;
        }
        return slots.get(0);
    }
}
