package dev.mtop.hungryloop.component;

import dev.mtop.hungryloop.HungryLoop;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Data components registered by the mod. */
public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTER =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, HungryLoop.MODID);

    public static final Supplier<DataComponentType<LoopContents>> LOOP_CONTENTS = REGISTER.register(
            "loop_contents",
            () -> DataComponentType.<LoopContents>builder()
                    .persistent(LoopContents.CODEC)
                    .networkSynchronized(LoopContents.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    private ModDataComponents() {}
}
