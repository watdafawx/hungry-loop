package dev.mtop.hungryloop.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.ModItems;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.curio.ContentForwarding;
import dev.mtop.hungryloop.integration.apotheosis.ApothLoot;
import dev.mtop.hungryloop.integration.relics.RelicsUnlock;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * {@code /hungryloop ...} test commands. Hand-registered from {@link HungryLoop} rather than via
 * {@code @EventBusSubscriber}: {@link #gen} reaches into Apotheosis and Relics (behind
 * {@link ModList} guards) and this keeps every such reference off a class FML loads eagerly.
 *
 * <ul>
 *   <li>{@code give [slots] [shiny]} - a loop with a chosen slot count.</li>
 *   <li>{@code gen [count] [sockets]} - fill the inventory with random curio/relic items, each
 *       rolled with Apotheosis affixes + gems (if present) and its Relics research unlocked (if
 *       present). Doubles as a diagnostic: every line reports the item's resolved sub-slot type and
 *       Apotheosis loot category.</li>
 * </ul>
 */
public final class HungryLoopCommands {

    private static final int DEFAULT_SOCKETS = 4;
    private static final int MAX_SOCKETS = 16;
    private static final int MAX_GEN = 36;

    private HungryLoopCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(HungryLoop.MODID)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("give")
                        .executes(ctx -> give(ctx.getSource(), LoopContents.MIN_BASE_SLOTS, false))
                        .then(Commands.argument("slots", IntegerArgumentType.integer(
                                        LoopContents.MIN_BASE_SLOTS, 24))
                                .executes(ctx -> give(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "slots"), false))
                                .then(Commands.argument("shiny", BoolArgumentType.bool())
                                        .executes(ctx -> give(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "slots"),
                                                BoolArgumentType.getBool(ctx, "shiny"))))))
                .then(Commands.literal("gen")
                        .executes(ctx -> gen(ctx.getSource(), -1, DEFAULT_SOCKETS))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_GEN))
                                .executes(ctx -> gen(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "count"), DEFAULT_SOCKETS))
                                .then(Commands.argument("sockets", IntegerArgumentType.integer(0, MAX_SOCKETS))
                                        .executes(ctx -> gen(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                IntegerArgumentType.getInteger(ctx, "sockets"))))))
                .then(Commands.literal("unlock")
                        .executes(ctx -> unlock(ctx.getSource()))));
    }

    private static int give(CommandSourceStack source, int slots, boolean shiny) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        int base = Math.min(slots, LoopContents.MAX_SHINY_BASE_SLOTS);
        int bonus = slots - base;
        // capBonus 0, then bump it just enough that this many slots fit under the cap
        LoopContents data = new LoopContents(base, bonus, 0, shiny, 0, List.of(), LoopContents.LOOP_MODE);
        int capBonus = Math.max(0, slots - data.slotCap());

        ItemStack loop = new ItemStack(ModItems.HUNGRY_LOOP.get());
        loop.set(ModDataComponents.LOOP_CONTENTS.get(), data.withCapBonus(capBonus));

        if (!player.getInventory().add(loop)) {
            player.drop(loop, false);
        }
        source.sendSuccess(() -> Component.translatable("commands.hungryloop.give.success", slots)
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int gen(CommandSourceStack source, int count, int sockets) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        List<Item> curios = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == ModItems.HUNGRY_LOOP.get()) continue;
            if (!CuriosApi.getItemStackSlots(new ItemStack(item), player).isEmpty()) {
                curios.add(item);
            }
        }
        if (curios.isEmpty()) {
            source.sendFailure(Component.translatable("commands.hungryloop.gen.no_curios"));
            return 0;
        }

        Collections.shuffle(curios, new Random(player.getRandom().nextLong()));

        int emptySlots = 0;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (player.getInventory().items.get(i).isEmpty()) emptySlots++;
        }
        int target = Math.min(count < 0 ? emptySlots : count, Math.min(curios.size(), MAX_GEN));

        boolean apotheosis = ModList.get().isLoaded(HungryLoop.APOTHEOSIS);
        boolean relics = ModList.get().isLoaded(HungryLoop.RELICS);
        int placed = 0;

        for (int i = 0; i < curios.size() && placed < target; i++) {
            ItemStack stack = new ItemStack(curios.get(i));
            String note;
            String category = "?";

            if (apotheosis) {
                ApothLoot.Result rolled = ApothLoot.decorate(player, stack, sockets);
                stack = rolled.stack();
                note = rolled.note();
                category = ApothLoot.lootCategory(stack);
            } else {
                note = "apotheosis absent";
            }

            if (relics && RelicsUnlock.unlockAll(player, stack)) {
                note = note + " + relic unlocked";
            }

            String slotType = ContentForwarding.resolveSlot(stack, player);

            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            placed++;

            ItemStack shown = stack;
            String line = note;
            String slot = slotType;
            String cat = category;
            source.sendSuccess(() -> Component.empty()
                    .append(shown.getHoverName())
                    .append(Component.literal(" — " + slot + " — " + cat + " — " + line)
                            .withStyle(ChatFormatting.GRAY)), false);
        }

        int total = placed;
        source.sendSuccess(() -> Component.translatable("commands.hungryloop.gen.success", total)
                .withStyle(ChatFormatting.GREEN), true);
        return placed;
    }

    /** Unlock every relic ability the player is carrying - inventory, curio slots, and inside loops. */
    private static int unlock(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!ModList.get().isLoaded(HungryLoop.RELICS)) {
            source.sendFailure(Component.literal("Relics is not installed."));
            return 0;
        }

        int count = 0;
        for (ItemStack stack : carriedStacks(player)) {
            if (RelicsUnlock.unlockAll(player, stack)) count++;
        }

        int total = count;
        source.sendSuccess(() -> Component.literal("Unlocked abilities on " + total + " relic(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static List<ItemStack> carriedStacks(ServerPlayer player) {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            all.add(player.getInventory().getItem(i));
        }
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            var equipped = handler.getEquippedCurios();
            for (int i = 0; i < equipped.getSlots(); i++) {
                all.add(equipped.getStackInSlot(i));
            }
        });
        for (ItemStack stack : List.copyOf(all)) {
            if (stack.getItem() instanceof dev.mtop.hungryloop.item.HungryLoopItem) {
                all.addAll(dev.mtop.hungryloop.item.HungryLoopItem.contentsOf(stack).items());
            }
        }
        return all;
    }
}
