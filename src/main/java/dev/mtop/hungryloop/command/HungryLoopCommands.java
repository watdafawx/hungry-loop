package dev.mtop.hungryloop.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.ModItems;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * {@code /hungryloop give [slots] [shiny]} - hands the player a loop with a chosen slot count. A
 * scaffold for the fuller test tooling (random affixed curios, ability unlocks) still on the roadmap.
 */
@EventBusSubscriber(modid = HungryLoop.MODID)
public final class HungryLoopCommands {

    private HungryLoopCommands() {}

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(HungryLoop.MODID)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("give")
                        .executes(ctx -> give(ctx.getSource(), LoopContents.MIN_BASE_SLOTS, false))
                        .then(Commands.argument("slots", IntegerArgumentType.integer(
                                        LoopContents.MIN_BASE_SLOTS, LoopContents.MAX_TOTAL_SLOTS))
                                .executes(ctx -> give(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "slots"), false))
                                .then(Commands.argument("shiny", BoolArgumentType.bool())
                                        .executes(ctx -> give(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "slots"),
                                                BoolArgumentType.getBool(ctx, "shiny")))))));
    }

    private static int give(CommandSourceStack source, int slots, boolean shiny) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        int base = Math.min(slots, LoopContents.MAX_SHINY_BASE_SLOTS);
        int bonus = slots - base;

        ItemStack loop = new ItemStack(ModItems.HUNGRY_LOOP.get());
        loop.set(ModDataComponents.LOOP_CONTENTS.get(),
                new LoopContents(base, bonus, shiny, 0, List.of()));

        if (!player.getInventory().add(loop)) {
            player.drop(loop, false);
        }

        source.sendSuccess(() -> Component.translatable("commands.hungryloop.give.success", slots)
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
