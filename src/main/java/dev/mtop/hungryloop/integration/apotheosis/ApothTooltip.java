package dev.mtop.hungryloop.integration.apotheosis;

import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.socket.SocketedGems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns an item's Apotheosis gem sockets into readable tooltip lines. The plain
 * {@code getTooltipLines} of a socketed item only carries a placeholder marker - Apotheosis swaps
 * that for a rendered gem grid in its own tooltip pass, which our nested tooltip doesn't get.
 *
 * <p><b>Optional dependency.</b> Only reached behind {@code ModList.get().isLoaded("apotheosis")}.
 */
public final class ApothTooltip {

    private ApothTooltip() {}

    /** True for the socket / attribute placeholder lines that only make sense with the gem grid. */
    public static boolean isPlaceholder(Component line) {
        String s = line.getString();
        return s.contains("SOCKET_MARKER") || s.contains("ATTRIB_MARKER");
    }

    /** One line per socket: the gem's bonus, or "Empty Socket". */
    public static List<Component> socketLines(ItemStack stack, Player player, Item.TooltipContext ctx, TooltipFlag flag) {
        int sockets = SocketHelper.getSockets(stack);
        if (sockets <= 0) return List.of();

        AttributeTooltipContext attrCtx = AttributeTooltipContext.of(player, ctx, flag);
        SocketedGems gems = SocketHelper.getGems(stack);

        List<Component> out = new ArrayList<>(sockets);
        for (int i = 0; i < sockets; i++) {
            if (i < gems.size() && gems.get(i) != null && gems.get(i).isValid()) {
                out.add(Component.literal("◆ ").withStyle(ChatFormatting.AQUA)
                        .append(gems.get(i).getSocketBonusTooltip(attrCtx)));
            } else {
                out.add(Component.literal("◇ ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.translatable("hungryloop.socket.empty")));
            }
        }
        return out;
    }
}
