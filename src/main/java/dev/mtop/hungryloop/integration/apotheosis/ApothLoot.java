package dev.mtop.hungryloop.integration.apotheosis;

import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;

/**
 * Rolls the best affixes and a full set of gems onto an item - the affix side of {@code /hungryloop
 * gen}. Lifted from the sibling {@code foxs-tweaks} project's {@code ApothicTestGear}.
 *
 * <p><b>Optional dependency.</b> Every symbol here is an Apotheosis type. Only ever call into this
 * class from behind {@code ModList.get().isLoaded("apotheosis")} - a reference from an eagerly
 * loaded class would {@code NoClassDefFoundError} on a pack without Apotheosis.
 */
public final class ApothLoot {

    /** {@link SocketHelper#setSockets} clamps here. */
    public static final int MAX_SOCKETS = 16;

    private ApothLoot() {}

    /** The decorated stack plus a short note on what actually happened (for the command's readout). */
    public record Result(ItemStack stack, String note) {}

    public static Result decorate(ServerPlayer player, ItemStack stack, int sockets) {
        GenContext ctx = GenContext.forPlayer(player);

        LootCategory category = LootCategory.forItem(stack);
        if (category.isNone()) {
            return new Result(stack, "no loot category - cannot be affixed");
        }

        LootRarity rarity = RarityRegistry.getSortedRarities().stream()
                .max(Comparator.comparingInt(LootRarity::sortIndex))
                .orElse(null);
        if (rarity == null) {
            return new Result(stack, "no loot rarities loaded");
        }

        // Affixes first: some rarity rules add sockets, and setSockets below would overwrite them.
        LootController.createLootItem(stack, category, rarity, ctx);
        SocketHelper.setSockets(stack, sockets);

        ItemStack result = stack;
        int filled = 0;

        for (int i = 0; i < sockets; i++) {
            ItemStack gem = GemRegistry.createRandomGemStack(ctx);
            if (gem.isEmpty()) break;

            GemItem.setPurity(gem, Purity.PERFECT);

            ItemStack socketed = SocketHelper.socketGemInItem(result, gem);
            if (socketed.isEmpty()) break;

            result = socketed;
            filled++;
        }

        return new Result(result, String.format("%s, %d affixes, %d/%d gems",
                category.getKey(), AffixHelper.streamAffixes(result).count(), filled, sockets));
    }

    /** Loot-category name for the diagnostic line, or {@code "none"}. */
    public static String lootCategory(ItemStack stack) {
        LootCategory category = LootCategory.forItem(stack);
        return category.isNone() ? "none" : category.getKey().toString();
    }
}
