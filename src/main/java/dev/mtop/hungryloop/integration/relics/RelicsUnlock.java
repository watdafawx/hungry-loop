package dev.mtop.hungryloop.integration.relics;

import it.hurts.sskirillss.relics.api.relics.IRelicItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Completes the constellation research for every ability on a relic, so {@code /hungryloop gen}
 * hands over relics whose abilities work immediately. Same walk of the Relics API that
 * {@code foxs-tweaks} uses for auto-solve-on-pickup.
 *
 * <p><b>Optional dependency.</b> {@link IRelicItem} is a Relics type - only reachable from behind
 * {@code ModList.get().isLoaded("relics")}.
 */
public final class RelicsUnlock {

    private RelicsUnlock() {}

    /** @return true if the stack was a relic (whether or not it had anything left to unlock). */
    public static boolean unlockAll(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof IRelicItem relic)) return false;

        var relicData = relic.getRelicData(player, stack);
        if (relicData.getTemplate() == null) return true;

        var abilities = relicData.getAbilitiesData();

        for (String ability : abilities.getAbilityIDs()) {
            var abilityData = abilities.getAbilityData(ability);
            if (abilityData == null) continue;

            var template = abilityData.getTemplate();
            if (template == null || template.getResearchTemplate().getStars().isEmpty()) continue;

            var research = abilityData.getResearchData();
            if (research.isResearched()) continue;

            research.complete();
        }
        return true;
    }
}
