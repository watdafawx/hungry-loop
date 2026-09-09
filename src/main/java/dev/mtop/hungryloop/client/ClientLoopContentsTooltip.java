package dev.mtop.hungryloop.client;

import dev.mtop.hungryloop.component.LoopContentsTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the loop's contents block: a "card" per contained item - its icon + the item's own
 * tooltip lines, and Apotheosis' own gem-socket renderer where the item is socketed.
 * {@value #CARDS_PER_PAGE} cards per page; page is {@link TooltipPager#page}.
 */
public class ClientLoopContentsTooltip implements ClientTooltipComponent {

    private static final int CARDS_PER_PAGE = 3;
    private static final int ICON = 18;
    private static final int SUBLINE = 10;
    private static final int INDENT = 20;

    /** A visual row: either an icon+text line, a plain sub-line, or an embedded component (sockets). */
    private record Row(ItemStack icon, FormattedCharSequence text, ClientTooltipComponent embedded, int height) {}

    private final List<Row> rows = new ArrayList<>();
    private int width;

    public ClientLoopContentsTooltip(LoopContentsTooltip data) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Item.TooltipContext ctx = Item.TooltipContext.of(mc.level);
        TooltipFlag flag = mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
        boolean apotheosis = ModList.get().isLoaded("apotheosis");

        List<ItemStack> items = data.contents();
        int pageCount = Math.max(1, (items.size() + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE);
        int page = Math.min(Math.max(TooltipPager.page, 0), pageCount - 1);
        TooltipPager.page = page;

        for (int c = page * CARDS_PER_PAGE; c < Math.min(items.size(), (page + 1) * CARDS_PER_PAGE); c++) {
            ItemStack stack = items.get(c);
            List<Component> lines = stack.getTooltipLines(ctx, mc.player, flag);

            Component name = Component.literal(c == data.selected() ? "▸ " : "")
                    .append(lines.isEmpty() ? stack.getHoverName() : lines.get(0));
            textRow(font, stack, name.getVisualOrderText(), ICON);

            for (int i = 1; i < lines.size(); i++) {
                Component line = lines.get(i);
                if (apotheosis && dev.mtop.hungryloop.integration.apotheosis.ApothTooltip.isPlaceholder(line)) {
                    continue;
                }
                textRow(font, ItemStack.EMPTY, line.getVisualOrderText(), SUBLINE);
            }
            if (apotheosis) {
                ClientTooltipComponent sockets =
                        dev.mtop.hungryloop.integration.apotheosis.ApothTooltip.socketRenderer(stack);
                if (sockets != null) {
                    embedRow(font, sockets);
                }
            }
            textRow(font, ItemStack.EMPTY, FormattedCharSequence.EMPTY, 4);
        }
        if (pageCount > 1) {
            textRow(font, ItemStack.EMPTY, Component.translatable("hungryloop.tooltip.page", page + 1, pageCount)
                    .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText(), SUBLINE);
        }
    }

    private void textRow(Font font, ItemStack icon, FormattedCharSequence text, int height) {
        rows.add(new Row(icon, text, null, height));
        width = Math.max(width, (icon.isEmpty() ? 4 : INDENT) + font.width(text));
    }

    private void embedRow(Font font, ClientTooltipComponent component) {
        rows.add(new Row(ItemStack.EMPTY, null, component, component.getHeight()));
        width = Math.max(width, INDENT + component.getWidth(font));
    }

    @Override
    public int getHeight() {
        return rows.stream().mapToInt(Row::height).sum() + 2;
    }

    @Override
    public int getWidth(Font font) {
        return width;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        int cy = y;
        for (Row row : rows) {
            if (row.embedded() != null) {
                row.embedded().renderText(font, x + INDENT, cy, matrix, buffer);
            } else {
                boolean card = !row.icon().isEmpty();
                font.drawInBatch(row.text(), x + (card ? INDENT : 4), cy + (card ? 5 : 0), 0xFFFFFFFF, true,
                        matrix, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }
            cy += row.height();
        }
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int cy = y;
        for (Row row : rows) {
            if (row.embedded() != null) {
                row.embedded().renderImage(font, x + INDENT, cy, graphics);
            } else if (!row.icon().isEmpty()) {
                graphics.renderFakeItem(row.icon(), x + 2, cy);
            }
            cy += row.height();
        }
    }
}
