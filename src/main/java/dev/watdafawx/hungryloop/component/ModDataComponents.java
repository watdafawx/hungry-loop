package dev.watdafawx.hungryloop.component;

import dev.watdafawx.hungryloop.HungryLoop;
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

    /**
     * Marks a stack as a loop-managed mirror: a copy the loop places into a real (granted) Curios
     * slot so other mods see it as equipped. The authoritative copy is in the loop's
     * {@link #LOOP_CONTENTS}; these mirrors are cleared and rebuilt by {@code curio.LoopSlotMirror}
     * and are made non-interactive in the Curios screen by {@code mixin.CurioSlotMixin}.
     */
    public static final Supplier<DataComponentType<Boolean>> LOOP_MANAGED = REGISTER.register(
            "loop_managed",
            () -> DataComponentType.<Boolean>builder()
                    .persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                    .build());

    private ModDataComponents() {}
}
