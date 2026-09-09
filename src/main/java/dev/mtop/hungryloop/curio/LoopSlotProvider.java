package dev.mtop.hungryloop.curio;

import dev.mtop.hungryloop.Config;
import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.item.HungryLoopItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * Server-side loop bookkeeping:
 * <ul>
 *   <li>grants every player the dedicated {@code hungryloop:loop} Curios slots -
 *   {@code Config.loopSlots()} of them, applied as a transient slot modifier so it tracks the
 *   (live-reloadable) config and leaves no NBT (the datapack registers the type at size 0);</li>
 *   <li>stamps a freshly-crafted loop with {@code Config.startSlots(false)} unlocked sub-slots.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HungryLoop.MODID)
public final class LoopSlotProvider {

    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(HungryLoop.MODID, "loop_slots");

    private LoopSlotProvider() {}

    @SubscribeEvent
    static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (result.getItem() instanceof HungryLoopItem
                && !result.has(ModDataComponents.LOOP_CONTENTS.get())) {
            result.set(ModDataComponents.LOOP_CONTENTS.get(),
                    new LoopContents(Config.startSlots(false), 0, 0, false, 0, List.of(),
                            LoopContents.LOOP_MODE, LoopContents.ALL_RENDER, true));
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) apply(player);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) apply(player);
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) apply(player);
    }

    /** Re-apply the configured count to every online player - call after a config reload. */
    public static void refreshAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(LoopSlotProvider::apply);
    }

    private static void apply(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            handler.removeSlotModifier(HungryLoopCurio.LOOP_SLOT, MODIFIER_ID);
            int count = Config.loopSlots();
            if (count > 0) {
                handler.addTransientSlotModifier(HungryLoopCurio.LOOP_SLOT, MODIFIER_ID, count,
                        AttributeModifier.Operation.ADD_VALUE);
            }
        });
    }
}
