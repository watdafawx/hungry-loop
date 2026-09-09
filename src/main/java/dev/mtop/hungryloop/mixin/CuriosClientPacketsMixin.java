package dev.mtop.hungryloop.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ICuriosMenu;
import top.theillusivec4.curios.client.gui.CuriosScreen;
import top.theillusivec4.curios.common.network.client.CuriosClientPackets;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two fixes to Curios' per-slot stack sync, both around the loop's granted mirror slots:
 *
 * <ol>
 *   <li><b>HEAD</b> - the loop grows a curio type and fills the new slot in the same server tick. If
 *   the per-slot sync lands before the slot-count sync, Curios writes a slot the client doesn't have
 *   yet and throws {@code Slot N not in valid range}, disconnecting the player. Drop the packet; the
 *   next full inventory sync carries the stack once the count is in place.</li>
 *   <li><b>TAIL</b> - unlike the full-inventory syncs, {@code SPacketSyncStack} never asks the open
 *   Curios screen to rebuild. When the stack it just wrote is one of our mirrors, the screen would
 *   keep showing that granted slot until the player closed and reopened it, so rebuild it now.</li>
 * </ol>
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

    @Inject(
            method = "handle(Ltop/theillusivec4/curios/common/network/server/sync/SPacketSyncStack;)V",
            at = @At("TAIL"), remap = false)
    private static void hungryloop$rebuildOpenCuriosScreen(SPacketSyncStack packet, CallbackInfo ci) {
        // A single-slot sync (unlike the full-inventory ones) doesn't rebuild the open Curios
        // screen, so a mirror written into a granted slot keeps showing until it's reopened.
        // Rebuild it here - it only fires while that screen is open and a curio stack just changed.
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && player.containerMenu instanceof ICuriosMenu menu) {
            menu.resetSlots();
            if (mc.screen instanceof CuriosScreen curiosScreen) {
                curiosScreen.updateRenderButtons(); // keep the render toggles in step with the slots
            }
        }
    }
}
