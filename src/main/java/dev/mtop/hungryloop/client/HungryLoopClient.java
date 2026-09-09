package dev.mtop.hungryloop.client;

import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.menu.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only wiring. Loaded only on a physical client (Dist.CLIENT on the annotation). */
@EventBusSubscriber(modid = HungryLoop.MODID, value = Dist.CLIENT)
public final class HungryLoopClient {

    private HungryLoopClient() {}

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.HUNGRY_LOOP.get(), HungryLoopScreen::new);
    }
}
