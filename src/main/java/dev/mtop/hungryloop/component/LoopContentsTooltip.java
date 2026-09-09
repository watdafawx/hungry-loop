package dev.mtop.hungryloop.component;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Carries the loop's contents to the client tooltip renderer ({@code client.ClientLoopContentsTooltip}),
 * which draws each item's icon + its own tooltip lines, paginated.
 */
public record LoopContentsTooltip(List<ItemStack> contents, int selected) implements TooltipComponent {
}
