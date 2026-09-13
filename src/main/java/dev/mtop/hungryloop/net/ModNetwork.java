package dev.mtop.hungryloop.net;

import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.item.HungryLoopItem;
import dev.mtop.hungryloop.menu.HungryLoopMenu;
import dev.mtop.hungryloop.menu.LoopLocator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Payload registration + server-side handlers. */
@EventBusSubscriber(modid = HungryLoop.MODID)
public final class ModNetwork {

    private ModNetwork() {}

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(OpenLoopPayload.TYPE, OpenLoopPayload.STREAM_CODEC, ModNetwork::onOpen);
    }

    private static void onOpen(OpenLoopPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        LoopLocator locator = findLoop(player);
        if (locator != null) {
            HungryLoopMenu.open(player, locator);
        }
    }

    /** First loop found on the player: hand, then inventory, then a curio slot. */
    private static LoopLocator findLoop(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof HungryLoopItem) {
                return LoopLocator.hand(hand);
            }
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof HungryLoopItem) {
                return LoopLocator.inventory(i);
            }
        }
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .flatMap(h -> h.findFirstCurio(s -> s.getItem() instanceof HungryLoopItem))
                .map(r -> LoopLocator.curio(r.slotContext().identifier(), r.slotContext().index()))
                .orElse(null);
    }
}
