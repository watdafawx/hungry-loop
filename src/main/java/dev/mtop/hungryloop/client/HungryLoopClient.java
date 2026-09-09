package dev.mtop.hungryloop.client;

import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.ModItems;
import dev.mtop.hungryloop.menu.ModMenus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only wiring. Loaded only on a physical client (Dist.CLIENT on the annotation). */
@EventBusSubscriber(modid = HungryLoop.MODID, value = Dist.CLIENT)
public final class HungryLoopClient {

    private HungryLoopClient() {}

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.HUNGRY_LOOP.get(), HungryLoopScreen::new);
    }

    @SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(HungryLoopKeys.OPEN);
    }

    @SubscribeEvent
    static void onRegisterDecorations(RegisterItemDecorationsEvent event) {
        event.register(ModItems.HUNGRY_LOOP.get(), new RelicBadgeDecorator());
    }

    @SubscribeEvent
    static void onRegisterTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(dev.mtop.hungryloop.component.LoopContentsTooltip.class, ClientLoopContentsTooltip::new);
    }

    /** One "+" over the loop when any contained relic has an ability to research or level up. */
    private static final class RelicBadgeDecorator implements IItemDecorator {
        @Override
        public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
            if (!ModList.get().isLoaded(HungryLoop.RELICS)) return false;
            if (!dev.mtop.hungryloop.integration.relics.RelicsBadge.anyContainedRelicWantsAttention(stack)) {
                return false;
            }

            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(font, Component.literal("+"), x + 1, y, 0xFF4AE04A, true);
            graphics.pose().popPose();
            return true;
        }
    }
}
