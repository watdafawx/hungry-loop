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
 * tooltip lines, and Apotheosis' own gem-socket renderer where the item is socketed. Cards are
 * paginated by actual height (not a fixed count) so a page never grows taller than the screen -
 * a single item with a long tooltip (enchants, affixes, sockets) can fill a page on its own.
 * Page is {@link TooltipPager#page}.
 */
public class ClientLoopContentsTooltip implements ClientTooltipComponent {

    private static final int ICON = 18;
    private static final int SUBLINE = 10;
    private static final int INDENT = 20;
    private static final int SPACER = 4;
    /** Faint alternating tint so a card's start/end reads at a glance. */
    private static final int TINT_A = 0x14FFFFFF;
    private static final int TINT_B = 0x00000000;
    /** Room for the loop's own name/slot-count lines above this component, and screen edges. */
    private static final int BASE_TOOLTIP_LINES = 4;
    /** Room for Curios' own "granted slots" block below this component: blank + gold header + our condensed line. */
    private static final int CURIOS_BLOCK_LINES = 3;

    /** A visual row: either an icon+text line, a plain sub-line, or an embedded component (sockets). */
    private record Row(ItemStack icon, FormattedCharSequence text, ClientTooltipComponent embedded, int height) {}

    /** One item's rendered rows and their total height, built once and paginated by height. */
    private record Card(List<Row> rows, int height) {}

    private final List<Row> rows = new ArrayList<>();
    /** [startY, boxHeight] per displayed card, boxHeight excluding its trailing spacer - for the tint. */
    private final List<int[]> cardBoxes = new ArrayList<>();
    private int width;

    public ClientLoopContentsTooltip(LoopContentsTooltip data) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Item.TooltipContext ctx = Item.TooltipContext.of(mc.level);
        TooltipFlag flag = mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
        boolean apotheosis = ModList.get().isLoaded("apotheosis");

        List<Card> cards = new ArrayList<>();
        for (ItemStack stack : data.contents()) {
            cards.add(buildCard(font, ctx, flag, apotheosis, stack, mc));
        }

        // Curios appends its own "granted slots" block right below this component - reserve exact
        // room for it (HungryLoopCurio condenses it to a fixed 3 lines) so the two together never
        // overflow the screen, instead of just guessing a margin.
        int reserved = (BASE_TOOLTIP_LINES + CURIOS_BLOCK_LINES) * SUBLINE;
        int maxPageHeight = Math.max(ICON + SUBLINE, mc.getWindow().getGuiScaledHeight() - reserved);
        List<int[]> pages = paginate(cards, maxPageHeight); // each entry: [fromCardIndex, toCardIndexExclusive]

        int pageCount = pages.size();
        int page = Math.min(Math.max(TooltipPager.page, 0), pageCount - 1);
        TooltipPager.page = page;

        int[] range = pages.get(page);
        int cy = 0;
        for (int c = range[0]; c < range[1]; c++) {
            Card card = cards.get(c);
            cardBoxes.add(new int[]{cy, card.height() - SPACER});
            for (Row row : card.rows()) {
                rows.add(row);
            }
            cy += card.height();
        }
        if (pageCount > 1) {
            rows.add(textRow(font, ItemStack.EMPTY, Component.translatable("hungryloop.tooltip.page", page + 1, pageCount)
                    .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText(), SUBLINE));
        }
    }

    private Card buildCard(Font font, Item.TooltipContext ctx, TooltipFlag flag, boolean apotheosis,
                            ItemStack stack, Minecraft mc) {
        List<Row> cardRows = new ArrayList<>();
        List<Component> lines = stack.getTooltipLines(ctx, mc.player, flag);

        Component name = lines.isEmpty() ? stack.getHoverName() : lines.get(0);
        cardRows.add(textRow(font, stack, name.getVisualOrderText(), ICON));

        for (int i = 1; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (apotheosis && dev.mtop.hungryloop.integration.apotheosis.ApothTooltip.isPlaceholder(line)) {
                continue;
            }
            cardRows.add(textRow(font, ItemStack.EMPTY, line.getVisualOrderText(), SUBLINE));
        }
        if (apotheosis) {
            ClientTooltipComponent sockets = dev.mtop.hungryloop.integration.apotheosis.ApothTooltip.socketRenderer(stack);
            if (sockets != null) {
                cardRows.add(embedRow(font, sockets));
            }
        }
        cardRows.add(textRow(font, ItemStack.EMPTY, FormattedCharSequence.EMPTY, SPACER));

        int height = cardRows.stream().mapToInt(Row::height).sum();
        return new Card(cardRows, height);
    }

    /** Greedily groups cards into pages that fit maxPageHeight; a single oversized card gets its own page. */
    private static List<int[]> paginate(List<Card> cards, int maxPageHeight) {
        List<int[]> pages = new ArrayList<>();
        if (cards.isEmpty()) {
            pages.add(new int[]{0, 0});
            return pages;
        }
        int start = 0;
        int height = 0;
        for (int i = 0; i < cards.size(); i++) {
            int cardHeight = cards.get(i).height();
            if (i > start && height + cardHeight > maxPageHeight) {
                pages.add(new int[]{start, i});
                start = i;
                height = 0;
            }
            height += cardHeight;
        }
        pages.add(new int[]{start, cards.size()});
        return pages;
    }

    private Row textRow(Font font, ItemStack icon, FormattedCharSequence text, int height) {
        width = Math.max(width, (icon.isEmpty() ? 4 : INDENT) + font.width(text));
        return new Row(icon, text, null, height);
    }

    private Row embedRow(Font font, ClientTooltipComponent component) {
        width = Math.max(width, INDENT + component.getWidth(font));
        return new Row(ItemStack.EMPTY, null, component, component.getHeight());
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
        for (int i = 0; i < cardBoxes.size(); i++) {
            int[] box = cardBoxes.get(i);
            graphics.fill(x - 2, y + box[0], x + width + 2, y + box[0] + box[1], i % 2 == 0 ? TINT_A : TINT_B);
        }

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
