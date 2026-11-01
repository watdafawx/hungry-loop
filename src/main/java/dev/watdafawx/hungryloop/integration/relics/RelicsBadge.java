package dev.watdafawx.hungryloop.integration.relics;

import dev.watdafawx.hungryloop.item.HungryLoopItem;
import it.hurts.sskirillss.relics.api.relics.IRelicItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * "Does any relic inside this loop want the player's attention" - has an ability that can be
 * researched or leveled up. Drives the single "+" badge {@code HungryLoopClient} draws on the loop.
 *
 * <p><b>Optional dependency.</b> {@link IRelicItem} - only reached behind
 * {@code ModList.get().isLoaded("relics")}.
 */
public final class RelicsBadge {

    private RelicsBadge() {}

    public static boolean anyContainedRelicWantsAttention(ItemStack loop) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;

        for (ItemStack inner : HungryLoopItem.contentsOf(loop).items()) {
            if (inner.isEmpty() || !(inner.getItem() instanceof IRelicItem relic)) continue;

            var relicData = relic.getRelicData(player, inner);
            if (relicData.getTemplate() == null) continue;

            var abilities = relicData.getAbilitiesData();
            for (String ability : abilities.getAbilityIDs()) {
                var abilityData = abilities.getAbilityData(ability);
                if (abilityData != null && (abilityData.mayUpgrade() || abilityData.mayResearch())) {
                    return true;
                }
            }
        }
        return false;
    }
}
