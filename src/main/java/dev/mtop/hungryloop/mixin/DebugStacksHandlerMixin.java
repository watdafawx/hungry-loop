package dev.mtop.hungryloop.mixin;

import dev.mtop.hungryloop.HungryLoop;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TEMP: trace every curio slot-type resize so we can see the loop-equip churn. */
@Mixin(CurioStacksHandler.class)
public abstract class DebugStacksHandlerMixin {

    @Shadow(remap = false) private String identifier;

    @Shadow(remap = false) public abstract int getSlots();

    @Inject(method = "resize", at = @At("HEAD"), remap = false)
    private void hungryloop$traceResize(int newSize, CallbackInfo ci) {
        HungryLoop.LOG.warn("[resize] {} {} -> {}", identifier, getSlots(), newSize,
                new Throwable("resize from"));
    }
}
