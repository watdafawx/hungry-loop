package dev.watdafawx.hungryloop;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** A one-item creative tab, so the loop is easy to find while testing. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HungryLoop.MODID);

    public static final Supplier<CreativeModeTab> MAIN = REGISTER.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hungryloop"))
            .icon(() -> new ItemStack(ModItems.HUNGRY_LOOP.get()))
            .displayItems((params, output) -> output.accept(ModItems.HUNGRY_LOOP.get()))
            .build());

    private ModCreativeTabs() {}
}
