package dev.mtop.hungryloop.mixin;

import dev.mtop.hungryloop.curio.CuriosRebuildGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.common.network.client.CuriosClientPackets;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncModifiers;

/**
 * Marks the span of Curios' client {@code SPacketSyncModifiers} handler, so
 * {@link CuriosRebuildGate} only ever considers skipping the rebuild that handler forces - never any
 * other {@code resetSlots()} caller. Hooks the handler method itself (a stable signature), not the
 * lambda inside it that actually calls {@code resetSlots()}.
 */
@Mixin(CuriosClientPackets.class)
public class CuriosModifiersSyncMixin {

    @Inject(method = "handle(Ltop/theillusivec4/curios/common/network/server/sync/SPacketSyncModifiers;)V",
            at = @At("HEAD"), remap = false)
    private static void hungryloop$enter(SPacketSyncModifiers packet, CallbackInfo ci) {
        CuriosRebuildGate.enterModifiersSync();
    }

    @Inject(method = "handle(Ltop/theillusivec4/curios/common/network/server/sync/SPacketSyncModifiers;)V",
            at = @At("RETURN"), remap = false)
    private static void hungryloop$exit(SPacketSyncModifiers packet, CallbackInfo ci) {
        CuriosRebuildGate.exitModifiersSync();
    }
}
