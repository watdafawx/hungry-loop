package dev.mtop.hungryloop.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.common.network.client.CuriosClientPackets;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The loop grows curio slot types and fills the new slots in the same server tick. If the per-slot
 * sync reaches the client before the slot-count sync, Curios tries to write a slot the client's
 * handler doesn't have yet and throws {@code Slot N not in valid range}, disconnecting the player.
 *
 * <p>This drops such a packet instead - the very next full inventory sync from Curios carries the
 * stack once the count is in place. Strictly safer than the crash; harmless for anyone else.
 */
@Mixin(CuriosClientPackets.class)
public class CuriosClientPacketsMixin {

    @Inject(
            method = "handle(Ltop/theillusivec4/curios/common/network/server/sync/SPacketSyncStack;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void hungryloop$dropOutOfRangeStackSync(SPacketSyncStack packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(packet.entityId()) instanceof LivingEntity entity)) {
            return;
        }
        int slots = CuriosApi.getCuriosInventory(entity)
                .flatMap(handler -> handler.getStacksHandler(packet.curioId()))
                .map(sh -> Math.max(sh.getStacks().getSlots(), sh.getCosmeticStacks().getSlots()))
                .orElse(0);
        if (packet.slotId() >= slots) {
            ci.cancel();
        }
    }
}
