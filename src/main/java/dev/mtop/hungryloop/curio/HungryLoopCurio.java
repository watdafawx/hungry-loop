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
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * The loop's Curios behaviour: it grants nothing of its own and instead forwards everything to its
 * contents, each as though equipped in its own native slot (see {@link ContentForwarding}).
 *
 * <p>Registered against {@code ModItems.HUNGRY_LOOP} from {@code FMLCommonSetupEvent}.
 */
public final class HungryLoopCurio implements ICurioItem {

    public static final HungryLoopCurio INSTANCE = new HungryLoopCurio();

    private HungryLoopCurio() {}

    private static LoopContents contents(ItemStack loop) {
        return loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return true; // the loop sits in the generic "curio" slot, which Curios accepts anywhere
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        ContentForwarding.each(slotContext, contents(stack), (curio, sub, s, i) -> curio.curioTick(sub));
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        ContentForwarding.each(slotContext, contents(stack), (curio, sub, s, i) -> curio.onEquip(sub, s));
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        ContentForwarding.each(slotContext, contents(stack), (curio, sub, s, i) -> curio.onUnequip(sub, s));
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext,
                                                                                ResourceLocation id,
                                                                                ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> all = HashMultimap.create();
        LoopContents contents = contents(stack);
        List<ItemStack> items = contents.items();

        for (int i = 0; i < items.size(); i++) {
            ItemStack contained = items.get(i);
            if (contained.isEmpty() || CuriosApi.getCurio(contained).isEmpty()) continue;

            String slotType = ContentForwarding.resolveSlot(contained, slotContext.entity());
            SlotContext sub = ContentForwarding.subContext(slotContext, slotType, i);
            ResourceLocation subId = id.withSuffix("/hl_" + i);
            int index = i;

            // Re-id every forwarded modifier so two copies of the same curio in one loop don't
            // collide on the wearer's AttributeMap (which is keyed by modifier id).
            CuriosApi.getAttributeModifiers(sub, subId, contained).forEach((attribute, modifier) ->
                    all.put(attribute, new AttributeModifier(
                            modifier.id().withSuffix("/hl_" + index),
                            modifier.amount(),
                            modifier.operation())));
        }
        return all;
    }
}
