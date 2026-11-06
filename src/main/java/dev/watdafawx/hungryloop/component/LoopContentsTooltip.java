package dev.watdafawx.hungryloop.component;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Carries the loop's contents to the client tooltip renderer ({@code client.ClientLoopContentsTooltip}),
 * which draws each item's icon + its own tooltip lines, paginated.
 */
/** {@code shiny} picks the frame palette (copper or aqua) the client draws the cards in. */
public record LoopContentsTooltip(List<ItemStack> contents, boolean shiny) implements TooltipComponent {
}
