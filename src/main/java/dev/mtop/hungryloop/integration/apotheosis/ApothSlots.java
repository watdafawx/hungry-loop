package dev.mtop.hungryloop.integration.apotheosis;

import dev.shadowsoffire.apothic_attributes.compat.CurioEquipmentSlot;
import dev.shadowsoffire.apothic_attributes.modifiers.EntityEquipmentSlot;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Which curio slot type Apotheosis reads an item's affixes from.
 *
 * <p>Affixes are gated by loot category, and a curio category's slot group is a set of
 * {@link CurioEquipmentSlot}s - each of which reads stacks from exactly one Curios handler,
 * {@code handler.getCurios().get(curioType)}. An affixed item therefore only works while it sits in
 * that one slot type. apothic_compats assigns categories by curio tag, so a wildcard item tagged
 * {@code curios:curio} (valid in <em>every</em> slot type) belongs to the {@code curio} category and
 * its affixes only fire from the {@code curio} slot - not from whichever type sorts first.
 *
 * <p>Only reached behind {@code ModList.get().isLoaded(HungryLoop.APOTHEOSIS)}.
 */
public final class ApothSlots {

    private ApothSlots() {}

    public static Optional<String> affixSlotType(ItemStack stack) {
        LootCategory category = LootCategory.forItem(stack);
        if (category == null || category.isNone()) return Optional.empty();
        for (Holder<EntityEquipmentSlot> slot : category.getSlots().slots()) {
            if (slot.value() instanceof CurioEquipmentSlot curio) return Optional.of(curio.curioType());
        }
        return Optional.empty();
    }
}
