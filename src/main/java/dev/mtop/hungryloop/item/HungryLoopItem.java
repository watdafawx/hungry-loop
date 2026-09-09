package dev.mtop.hungryloop.item;

import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.menu.HungryLoopMenu;
import dev.mtop.hungryloop.menu.LoopLocator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The loop item.
 *
 * <ul>
 *   <li><b>Loop mode</b> (default): right-click - held or in an inventory GUI - opens the sub-slot
 *       menu. A keybind opens it from anywhere ({@code net.OpenLoopPayload}).</li>
 *   <li><b>Passthrough mode</b>: shift-scroll while holding the loop cycles a selected contained
 *       item; right-click then forwards to that item's own use, for triggering a contained relic.
 *       (Items that read the held stack mid-use - charge-up, use-duration - won't behave perfectly;
 *       instant right-click abilities do.)</li>
 * </ul>
 */
public class HungryLoopItem extends Item {

    public HungryLoopItem(Properties properties) {
        super(properties);
    }

    public static LoopContents contentsOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
    }

    /** Replace the currently-selected contained item (e.g. after its use() consumed durability). */
    private static void writeBackSelected(ItemStack loop, LoopContents data, ItemStack updatedInner) {
        List<ItemStack> items = new ArrayList<>(data.items());
        if (data.selected() >= 0 && data.selected() < items.size()) {
            items.set(data.selected(), updatedInner);
            loop.set(ModDataComponents.LOOP_CONTENTS.get(), data.withItems(items));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack loop = player.getItemInHand(hand);
        LoopContents data = contentsOf(loop);

        if (!data.loopMode()) {
            ItemStack inner = data.selectedStack().copy();
            InteractionResultHolder<ItemStack> result = inner.getItem().use(level, player, hand);
            if (!level.isClientSide) {
                writeBackSelected(loop, data, result.getObject());
            }
            return new InteractionResultHolder<>(result.getResult(), loop);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            HungryLoopMenu.open(serverPlayer, LoopLocator.hand(hand));
        }
        return InteractionResultHolder.sidedSuccess(loop, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack loop = context.getItemInHand();
        LoopContents data = contentsOf(loop);
        if (data.loopMode()) return InteractionResult.PASS;

        InteractionResult result = data.selectedStack().getItem().useOn(context);
        if (!context.getLevel().isClientSide) {
            writeBackSelected(loop, data, context.getItemInHand());
        }
        return result;
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
    public Component getName(ItemStack stack) {
        LoopContents data = contentsOf(stack);
        if (!data.loopMode()) {
            return Component.translatable("hungryloop.name.passthrough", data.selectedStack().getHoverName());
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LoopContents data = contentsOf(stack);

        long used = data.items().stream().filter(s -> !s.isEmpty()).count();
        tooltip.add(Component.translatable("hungryloop.tooltip.slots", used, data.totalSlots())
                .withStyle(ChatFormatting.GRAY));

        if (!data.loopMode()) {
            tooltip.add(Component.translatable("hungryloop.tooltip.selected", data.selectedStack().getHoverName())
                    .withStyle(ChatFormatting.GOLD));
        }

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
