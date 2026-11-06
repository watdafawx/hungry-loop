package dev.watdafawx.hungryloop.integration.relics;

import it.hurts.sskirillss.relics.api.relics.IRelicItem;
import it.hurts.sskirillss.relics.utils.EntityUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Dev-only hooks for {@code client.SelfTest}: Relics' own view of the player. Only reached behind
 * {@code ModList.isLoaded("relics")}.
 */
public final class RelicsTestHooks {

    private RelicsTestHooks() {}

    /** Relic items, sorted by id, deterministic. */
    public static List<Item> relicItems(int max) {
        return BuiltInRegistries.ITEM.keySet().stream().sorted()
                .map(BuiltInRegistries.ITEM::get)
                .filter(i -> i instanceof IRelicItem)
                .limit(max)
                .toList();
    }

    /** What every relic ability uses to find its item on the player. */
    public static boolean relicsSeesEquipped(Player player, Item item) {
        return !EntityUtils.findEquippedCurio(player, item).isEmpty();
    }

    /** Every ability with a template is researched (where it has research) and at level 1+. */
    public static boolean isUnlocked(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof IRelicItem relic)) return false;
        var abilities = relic.getRelicData(player, stack).getAbilitiesData();
        boolean any = false;
        for (String id : abilities.getAbilityIDs()) {
            var ability = abilities.getAbilityData(id);
            if (ability == null || ability.getTemplate() == null) continue;
            any = true;
            if (ability.getLevel() < 1) return false;
            if (!ability.getTemplate().getResearchTemplate().getStars().isEmpty()
                    && !ability.getResearchData().isResearched()) return false;
        }
        return any;
    }
}
