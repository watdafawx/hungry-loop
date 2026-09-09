package dev.mtop.hungryloop;

import dev.mtop.hungryloop.command.HungryLoopCommands;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.curio.HungryLoopCurio;
import dev.mtop.hungryloop.curio.LoopSlotProvider;
import dev.mtop.hungryloop.loot.ShinyLoopModifier;
import dev.mtop.hungryloop.menu.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * The Hungry Loop - a single curio that holds other curios and grants their effects as if each were
 * equipped in its own native slot.
 *
 * <p><b>Optional dependencies.</b> Only Curios is required. Apotheosis, Relics and Artifacts
 * integrations (still to be built) must never be referenced from a class FML loads eagerly - keep
 * them under {@code integration/} behind a {@code ModList.get().isLoaded(...)} check.
 */
@Mod(HungryLoop.MODID)
public class HungryLoop {
    public static final String MODID = "hungryloop";
    public static final Logger LOG = LoggerFactory.getLogger("The Hungry Loop");

    public static final String CURIOS = "curios";
    public static final String APOTHEOSIS = "apotheosis";
    public static final String RELICS = "relics";
    public static final String ARTIFACTS = "artifacts";

    public HungryLoop(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        ModItems.REGISTER.register(modBus);
        ModCreativeTabs.REGISTER.register(modBus);
        ModDataComponents.REGISTER.register(modBus);
        ModMenus.REGISTER.register(modBus);
        ShinyLoopModifier.REGISTER.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::onConfigLoad);

        // Hand-registered, not @EventBusSubscriber: the gen command reaches into Apotheosis and
        // Relics (behind ModList guards) and this keeps that off an eagerly loaded class.
        NeoForge.EVENT_BUS.register(HungryLoopCommands.class);

        LOG.info("The Hungry Loop is listening.");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // registerCurio touches the frozen item registry - safe on the setup thread.
        event.enqueueWork(() ->
                CuriosApi.registerCurio(ModItems.HUNGRY_LOOP.get(), HungryLoopCurio.INSTANCE));
    }

    private void onConfigLoad(ModConfigEvent event) {
        // A live reload of loopSlots should reach players who are already online.
        if (event.getConfig().getSpec() == Config.SPEC) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> LoopSlotProvider.refreshAll(server));
            }
        }
    }
}
