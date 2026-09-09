package dev.mtop.hungryloop.integration.relics;

import it.hurts.sskirillss.relics.api.relics.IRelicItem;
import it.hurts.sskirillss.relics.api.relics.data.AbilityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Unlocks every ability on a relic - research complete and the ability leveled to at least 1 - so
 * {@code /hungryloop gen} (and {@code /hungryloop unlock}) hand over relics that work immediately.
 *
 * <p><b>Optional dependency.</b> Reachable only from behind {@code ModList.get().isLoaded("relics")}.
 */
public final class RelicsUnlock {

    private RelicsUnlock() {}

    /** @return true if the stack was a relic. */
    public static boolean unlockAll(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof IRelicItem relic)) return false;

        var relicData = relic.getRelicData(player, stack);
        if (relicData.getTemplate() == null) return true;

        var abilities = relicData.getAbilitiesData();

        for (String ability : abilities.getAbilityIDs()) {
            AbilityData abilityData = abilities.getAbilityData(ability);
            if (abilityData == null || abilityData.getTemplate() == null) continue;

            // 1. Research: fill in the correct constellation links and mark it solved.
            var research = abilityData.getResearchData();
            if (!abilityData.getTemplate().getResearchTemplate().getStars().isEmpty()
                    && !research.isResearched()) {
                research.complete();
            }

            // 2. Unlock: an ability at level 0 is researched-but-not-active.
            if (abilityData.getLevel() < 1) {
                abilityData.setLevel(1);
            }
        }
        return true;
    }
}
