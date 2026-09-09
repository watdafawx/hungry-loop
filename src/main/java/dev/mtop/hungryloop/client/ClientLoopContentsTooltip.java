package dev.mtop.hungryloop.client;

import dev.mtop.hungryloop.component.LoopContentsTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the loop's contents block: a "card" per contained item (16px icon + name + that item's own
 * tooltip lines, indented), {@value #CARDS_PER_PAGE} cards per page. Page is {@link TooltipPager#page},
 * bumped by scrolling over the loop.
 */
public class ClientLoopContentsTooltip implements ClientTooltipComponent {

    private static final int CARDS_PER_PAGE = 3;
    private static final int ICON = 18;
    private static final int SUBLINE = 10;

    private record Row(ItemStack icon, FormattedCharSequence text, int height) {}

    private final List<Row> rows = new ArrayList<>();
    private int width;

    public ClientLoopContentsTooltip(LoopContentsTooltip data) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Item.TooltipContext ctx = Item.TooltipContext.of(mc.level);
        TooltipFlag flag = mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;

        List<ItemStack> items = data.contents();
        int pageCount = Math.max(1, (items.size() + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE);
        int page = Math.min(Math.max(TooltipPager.page, 0), pageCount - 1);
        TooltipPager.page = page; // clamp the shared state so scrolling can't run off the end

        for (int c = page * CARDS_PER_PAGE; c < Math.min(items.size(), (page + 1) * CARDS_PER_PAGE); c++) {
            ItemStack stack = items.get(c);
            List<Component> lines = stack.getTooltipLines(ctx, mc.player, flag);

            Component name = Component.literal(c == data.selected() ? "▸ " : "")
                    .append(lines.isEmpty() ? stack.getHoverName() : lines.get(0));
            addRow(font, stack, name.getVisualOrderText(), ICON);

            for (int i = 1; i < lines.size(); i++) {
                addRow(font, ItemStack.EMPTY, lines.get(i).getVisualOrderText(), SUBLINE);
            }
            addRow(font, ItemStack.EMPTY, FormattedCharSequence.EMPTY, 4);
        }
        if (pageCount > 1) {
            addRow(font, ItemStack.EMPTY, Component.translatable("hungryloop.tooltip.page", page + 1, pageCount)
                    .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText(), SUBLINE);
        }
    }

    private void addRow(Font font, ItemStack icon, FormattedCharSequence text, int height) {
        rows.add(new Row(icon, text, height));
        int indent = icon.isEmpty() ? 4 : 20;
        width = Math.max(width, indent + font.width(text));
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
            boolean card = !row.icon().isEmpty();
            font.drawInBatch(row.text(), x + (card ? 20 : 4), cy + (card ? 5 : 0), 0xFFFFFFFF, true,
                    matrix, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            cy += row.height();
        }
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int cy = y;
        for (Row row : rows) {
            if (!row.icon().isEmpty()) {
                graphics.renderFakeItem(row.icon(), x + 2, cy);
            }
            cy += row.height();
        }
    }
}
