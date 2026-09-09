package dev.mtop.hungryloop;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Hungry Loop - a single curio that holds other curios and grants their effects as if each were
 * equipped in its own native slot.
 *
 * <p>This is the scaffold entry point. Registration is wired here from the mod event bus; the actual
 * behaviour (adaptive inventory, effect forwarding, XP-fed slot growth, the overlay UI) is still to
 * be built - see TODO.md.
 *
 * <p><b>Optional dependencies.</b> Only Curios is required. Apotheosis, Relics and Artifacts
 * integrations must never be referenced from a class that FML loads eagerly (anything with
 * {@code @EventBusSubscriber}, or touched from this constructor without a guard). Each lives under
 * {@code integration/} and is entered only behind a {@code ModList.get().isLoaded(...)} check.
 */
@Mod(HungryLoop.MODID)
public class HungryLoop {
    public static final String MODID = "hungryloop";
    public static final Logger LOG = LoggerFactory.getLogger("The Hungry Loop");

    // Mod ids of the optional integration targets.
    public static final String CURIOS = "curios";
    public static final String APOTHEOSIS = "apotheosis";
    public static final String RELICS = "relics";
    public static final String ARTIFACTS = "artifacts";

    public HungryLoop(IEventBus modBus, ModContainer container) {
        ModItems.REGISTER.register(modBus);
        ModCreativeTabs.REGISTER.register(modBus);

        LOG.info("The Hungry Loop is listening.");
    }
}
