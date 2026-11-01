package dev.watdafawx.hungryloop.integration.apotheosis;

import dev.shadowsoffire.apotheosis.client.SocketTooltipRenderer;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Reuses Apotheosis' own gem-socket tooltip renderer inside the loop's contents tooltip, so sockets
 * and their gems look exactly as they do on the item itself.
 *
 * <p>The plain {@code getTooltipLines} of a socketed item only carries a placeholder marker;
 * Apotheosis swaps that for {@link SocketTooltipRenderer} in a {@code RenderTooltipEvent} pass our
 * nested tooltip never sees, so we build the same component here.
 *
 * <p><b>Optional dependency.</b> Only reached behind {@code ModList.get().isLoaded("apotheosis")}.
 */
public final class ApothTooltip {

    private ApothTooltip() {}

    /** True for the socket / attribute placeholder lines that only make sense as the gem grid. */
    public static boolean isPlaceholder(Component line) {
        String s = line.getString();
        return s.contains("SOCKET_MARKER") || s.contains("ATTRIB_MARKER");
    }

    /** Apotheosis' socket renderer for {@code stack}, or {@code null} if it has no sockets. */
    public static ClientTooltipComponent socketRenderer(ItemStack stack) {
        if (SocketHelper.getSockets(stack) <= 0) return null;
        return new SocketTooltipRenderer(
                new SocketTooltipRenderer.SocketComponent(stack, SocketHelper.getGems(stack)));
    }
}
