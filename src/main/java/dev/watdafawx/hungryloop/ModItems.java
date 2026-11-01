package dev.watdafawx.hungryloop;

import dev.watdafawx.hungryloop.item.HungryLoopItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Item registry. */
public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(HungryLoop.MODID);

    public static final Supplier<HungryLoopItem> HUNGRY_LOOP = REGISTER.register(
            "hungry_loop",
            () -> new HungryLoopItem(new Item.Properties().stacksTo(1).fireResistant()));

    private ModItems() {}
}
