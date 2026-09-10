package dev.mtop.hungryloop.mixin;

import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.curio.LoopSlotMirror;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TEMP: dump exactly which CurioSlots the screen ends up with, on both sides. */
@Mixin(CuriosContainer.class)
public abstract class DebugSetPageMixin {

    @Shadow public Player player;

    @Inject(method = "setPage", at = @At("TAIL"), remap = false)
    private void hungryloop$dumpSlots(int page, CallbackInfo ci) {
        String side = player.level().isClientSide ? "CLIENT" : "SERVER";
        StringBuilder sb = new StringBuilder();
        int curio = 0;
        for (Slot s : ((AbstractContainerMenu) (Object) this).slots) {
            if (!(s instanceof CurioSlot cs)) continue;
            curio++;
            ItemStack st = s.getItem();
            sb.append("\n  ").append(cs.getIdentifier()).append('#').append(s.getSlotIndex())
              .append(" item=").append(st.isEmpty() ? "-" : st.getItem())
              .append(" mirror=").append(LoopSlotMirror.isMirror(st))
              .append(" active=").append(s.isActive())
              .append(" xy=").append(s.x).append(',').append(s.y);
        }
        HungryLoop.LOG.warn("[setPage {}] page={} curioSlots={}{}", side, page, curio, sb);
    }
}
