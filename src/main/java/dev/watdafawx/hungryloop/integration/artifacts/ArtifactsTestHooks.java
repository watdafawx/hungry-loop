package dev.watdafawx.hungryloop.integration.artifacts;

import artifacts.equipment.EquipmentHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Dev-only hooks for {@code client.SelfTest}: Artifacts' own view of the player. Every Artifacts
 * ability (EquipmentHelper.hasAbilityActive, sumInt, iterateAbilities, ...) walks the equipment this
 * visits, so an item missing here has no working abilities. Only reached behind
 * {@code ModList.isLoaded("artifacts")}.
 */
public final class ArtifactsTestHooks {

    private ArtifactsTestHooks() {}

    public static List<Item> artifactItems(int max) {
        return BuiltInRegistries.ITEM.keySet().stream().sorted()
                .filter(id -> id.getNamespace().equals("artifacts"))
                .map(BuiltInRegistries.ITEM::get)
                .limit(max)
                .toList();
    }

    public static boolean artifactsSeesEquipped(LivingEntity entity, Item item) {
        boolean[] seen = {false};
        EquipmentHelper.iterateEquipment(entity, stack -> {
            if (stack.is(item)) seen[0] = true;
        });
        return seen[0];
    }
}
