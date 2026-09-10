package dev.mtop.hungryloop.mixin;

import dev.mtop.hungryloop.client.PendingCurioStackSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
 *   <li><b>HEAD</b> - the loop grows a curio type and fills the new slot in the same server tick, but
 *   the stack sync goes out before the slot-count update. Applying it early throws {@code Slot N not
 *   in valid range} and disconnects. Hold the packet in {@link PendingCurioStackSync} and replay it
 *   when the count catches up - dropping it outright lost the mirror for good, because the server
 *   has nothing further to send for a slot it considers settled.</li>
 *   <li><b>TAIL</b> - unlike the full-inventory syncs, {@code SPacketSyncStack} never asks the open
 *   Curios screen to rebuild, so a mirror written into a granted slot would keep showing until the
 *   screen was reopened. Rebuild it (and its render toggles) here.</li>
 * </ol>
 */
@Mixin(CuriosClientPackets.class)
public class CuriosClientPacketsMixin {

    @Inject(
            method = "handle(Ltop/theillusivec4/curios/common/network/server/sync/SPacketSyncStack;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void hungryloop$deferOutOfRangeStackSync(SPacketSyncStack packet, CallbackInfo ci) {
        if (!PendingCurioStackSync.slotExists(packet)) {
            PendingCurioStackSync.defer(packet);
            ci.cancel();
        }
    }

    @Inject(
            method = "handle(Ltop/theillusivec4/curios/common/network/server/sync/SPacketSyncStack;)V",
            at = @At("TAIL"), remap = false)
    private static void hungryloop$rebuildOpenCuriosScreen(SPacketSyncStack packet, CallbackInfo ci) {
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
