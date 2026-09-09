package dev.mtop.hungryloop.item;

import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.menu.HungryLoopMenu;
import dev.mtop.hungryloop.menu.LoopLocator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The loop. Curios behaviour is in {@code curio.HungryLoopCurio}; this class is the item: it opens
 * the sub-slot menu (right-click it in any inventory GUI, or use it in hand) and summarises its
 * contents in the tooltip.
 */
public class HungryLoopItem extends Item {

    public HungryLoopItem(Properties properties) {
        super(properties);
    }

    public static LoopContents contentsOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
    }

    private static void open(ServerPlayer player, ItemStack loop, LoopLocator locator) {
        MenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> new HungryLoopMenu(id, inv, locator),
                loop.getHoverName());
        player.openMenu(provider, locator::encode);
    }

    /** Right-click the loop where it sits in an inventory screen (cursor empty). */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot,
                                            ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !other.isEmpty()) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            LoopLocator locator = LoopLocator.find(serverPlayer, stack);
            if (locator != null) {
                open(serverPlayer, stack, locator);
            }
        }
        return true; // swallow the click on both sides so the loop isn't picked up
    }

    /** Right-click the loop while holding it. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, stack, LoopLocator.hand(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
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
        } else {
            tooltip.add(Component.translatable("hungryloop.tooltip.contents").withStyle(ChatFormatting.GRAY));
            for (ItemStack contained : data.items()) {
                if (contained.isEmpty()) continue;
                tooltip.add(Component.literal("  ").append(contained.getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return contentsOf(stack).shiny();
    }
}
