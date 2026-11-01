package dev.watdafawx.hungryloop.menu;

import dev.watdafawx.hungryloop.HungryLoop;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Menu type registry. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER =
            DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, HungryLoop.MODID);

    public static final Supplier<MenuType<HungryLoopMenu>> HUNGRY_LOOP = REGISTER.register(
            "hungry_loop",
            () -> IMenuTypeExtension.create(HungryLoopMenu::new));

    private ModMenus() {}
}
