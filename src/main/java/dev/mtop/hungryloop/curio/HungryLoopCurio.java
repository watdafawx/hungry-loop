package dev.mtop.hungryloop.curio;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotAttribute;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.HashMap;
import java.util.Map;

/**
 * The loop's Curios behaviour. It grants nothing of its own and forwards no effects directly -
 * instead, while worn, it grants the wearer one extra Curios slot per contained item, of that
 * item's resolved type. {@code LoopSlotMirror} fills those slots with the loop's contents, so Curios
 * itself ticks them, applies their attributes, and every mod that scans the Curios inventory
 * (Apotheosis, Relics, Artifacts, ...) sees them equipped in their own slot.
 *
 * <p>Registered against {@code ModItems.HUNGRY_LOOP} from {@code FMLCommonSetupEvent}.
 */
public final class HungryLoopCurio implements ICurioItem {

    public static final HungryLoopCurio INSTANCE = new HungryLoopCurio();

    private HungryLoopCurio() {}

    private static LoopContents contents(ItemStack loop) {
        return loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
    }

    /** The loop's dedicated Curios slot type; its size is driven by {@code Config.loopSlots()}. */
    public static final String LOOP_SLOT = "loop";

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return slotContext.identifier().equals(LOOP_SLOT);
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext,
                                                                                ResourceLocation id,
                                                                                ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create();

        // One extra slot per contained item, grouped by the type it resolves to. Curios grows and
        // shrinks these with the loop's equip state; LoopSlotMirror fills them.
        Map<String, Integer> grantsByType = new HashMap<>();
        for (ItemStack contained : contents(stack).items()) {
            if (contained.isEmpty()) continue;
            grantsByType.merge(ContentForwarding.resolveSlot(contained, slotContext.entity()), 1, Integer::sum);
        }

        grantsByType.forEach((type, count) -> modifiers.put(
                SlotAttribute.getOrCreate(type),
                new AttributeModifier(id.withSuffix("/hl_slot_" + type), count,
                        AttributeModifier.Operation.ADD_VALUE)));

        return modifiers;
    }
}
