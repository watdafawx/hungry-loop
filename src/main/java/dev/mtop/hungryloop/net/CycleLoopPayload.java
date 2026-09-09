package dev.mtop.hungryloop.net;

import dev.mtop.hungryloop.HungryLoop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: shift-scroll on a held loop cycled the passthrough selection. */
public record CycleLoopPayload(int direction) implements CustomPacketPayload {

    public static final Type<CycleLoopPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HungryLoop.MODID, "cycle_loop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CycleLoopPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CycleLoopPayload::direction, CycleLoopPayload::new);

    @Override
    public Type<CycleLoopPayload> type() {
        return TYPE;
    }
}
