package dev.mtop.hungryloop.item;

import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.menu.HungryLoopMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The loop. Its Curios behaviour lives in {@code curio.HungryLoopCurio}; this class is just the item:
 * right-click opens the sub-slot menu, and the tooltip summarises what's inside.
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
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new HungryLoopMenu(id, inv, hand),
                    stack.getHoverName());
            serverPlayer.openMenu(provider, buf -> buf.writeEnum(hand));
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
