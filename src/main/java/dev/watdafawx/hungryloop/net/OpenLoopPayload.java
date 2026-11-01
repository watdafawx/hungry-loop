package dev.watdafawx.hungryloop.net;

import dev.watdafawx.hungryloop.HungryLoop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "open the Hungry Loop I'm carrying". The server finds it. */
public record OpenLoopPayload() implements CustomPacketPayload {

    public static final Type<OpenLoopPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HungryLoop.MODID, "open_loop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLoopPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenLoopPayload());

    @Override
    public Type<OpenLoopPayload> type() {
        return TYPE;
    }
}
