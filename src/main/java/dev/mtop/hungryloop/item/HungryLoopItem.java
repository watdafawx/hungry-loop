package dev.mtop.hungryloop.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The loop itself.
 *
 * <p>Scaffold only. What it still needs (see TODO.md):
 * <ul>
 *   <li>An adaptive inventory stored on the stack (data component) - each occupied sub-slot takes on
 *       the Curios slot type of whatever was placed in it.</li>
 *   <li>Curios {@code ICurio} behaviour so the loop is equippable, forwarding the attribute
 *       modifiers, Apotheosis affixes/sockets and Relics/Artifacts effects of its contents as though
 *       each were in its own slot.</li>
 *   <li>XP-fed slot growth (4 base, 4-8 for shiny loot loops, rising cost per slot).</li>
 *   <li>Right-click / hotkey opens an overlay screen without closing the current one.</li>
 *   <li>A contents tooltip with drag-in/drag-out.</li>
 * </ul>
 */
public class HungryLoopItem extends Item {
    public HungryLoopItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("hungryloop.tooltip.contents").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("hungryloop.tooltip.empty").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
