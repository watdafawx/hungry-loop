package dev.mtop.hungryloop.item;

import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.LoopContentsTooltip;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.menu.HungryLoopMenu;
import dev.mtop.hungryloop.menu.LoopLocator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * The loop item. Right-click - held or in an inventory GUI - opens the sub-slot menu. A keybind
 * opens it from anywhere ({@code net.OpenLoopPayload}).
 */
public class HungryLoopItem extends Item {

    public HungryLoopItem(Properties properties) {
        super(properties);
    }

    public static LoopContents contentsOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack loop = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            HungryLoopMenu.open(serverPlayer, LoopLocator.hand(hand));
        }
        return InteractionResultHolder.sidedSuccess(loop, level.isClientSide());
    }

    /** Right-click the loop where it sits in an inventory screen (empty cursor). */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot,
                                            ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !other.isEmpty()) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            LoopLocator locator = LoopLocator.find(serverPlayer, stack);
            if (locator == null) return false;
            // Defer: opening a menu from inside container-click handling upsets the current menu.
            serverPlayer.server.execute(() -> HungryLoopMenu.open(serverPlayer, locator));
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LoopContents data = contentsOf(stack);

        long used = data.items().stream().filter(s -> !s.isEmpty()).count();
        tooltip.add(Component.translatable("hungryloop.tooltip.slots", used, data.totalSlots())
                .withStyle(ChatFormatting.GRAY));

        if (data.shiny()) {
            tooltip.add(Component.translatable("hungryloop.tooltip.shiny", data.baseSlots())
                    .withStyle(ChatFormatting.AQUA));
        }

        if (used == 0) {
            tooltip.add(Component.translatable("hungryloop.tooltip.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
        // The detailed contents (names, affixes, attributes, gems, paginated) are appended
        // client-side by client.HungryLoopTooltip - it has the tooltip context this method lacks
        // and must never run on a dedicated server.
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        List<ItemStack> items = contentsOf(stack).items().stream().filter(s -> !s.isEmpty()).toList();
        return items.isEmpty() ? Optional.empty() : Optional.of(new LoopContentsTooltip(items));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return contentsOf(stack).shiny();
    }
}
