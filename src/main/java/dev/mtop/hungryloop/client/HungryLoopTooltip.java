package dev.mtop.hungryloop.client;

import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.item.HungryLoopItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Appends the loop's rich contents block to its tooltip: every contained item's own tooltip
 * (name, affixes, attributes, gem sockets - however that mod formats them), indented, paginated.
 * Scroll over the loop in an inventory screen to change page ({@link HungryLoopKeys}).
 */
@EventBusSubscriber(modid = HungryLoop.MODID, value = Dist.CLIENT)
public final class HungryLoopTooltip {

    private HungryLoopTooltip() {}

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof HungryLoopItem)) return;

        LoopContents data = HungryLoopItem.contentsOf(stack);
        List<ItemStack> items = data.items().stream().filter(s -> !s.isEmpty()).toList();
        if (items.isEmpty()) return;

        List<Component> detail = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack contained = items.get(i);
            boolean isSelected = i == data.selected();

            detail.add(Component.literal((isSelected ? "▸ " : "❖ "))
                    .append(contained.getHoverName())
                    .withStyle(isSelected ? ChatFormatting.GOLD : ChatFormatting.WHITE));

            List<Component> sub = contained.getTooltipLines(event.getContext(), event.getEntity(), event.getFlags());
            for (int line = 1; line < sub.size(); line++) { // skip line 0 (the name, added above)
                detail.add(Component.literal("  ").append(sub.get(line)));
            }
            detail.add(Component.empty());
        }
        if (!detail.isEmpty() && detail.get(detail.size() - 1).getString().isEmpty()) {
            detail.remove(detail.size() - 1);
        }

        int perPage = TooltipPager.LINES_PER_PAGE;
        int pages = Math.max(1, (detail.size() + perPage - 1) / perPage);
        int page = Math.min(Math.max(TooltipPager.page, 0), pages - 1);

        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("hungryloop.tooltip.contents").withStyle(ChatFormatting.GRAY));
        tooltip.addAll(detail.subList(page * perPage, Math.min(detail.size(), (page + 1) * perPage)));

        if (pages > 1) {
            tooltip.add(Component.translatable("hungryloop.tooltip.page", page + 1, pages)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
