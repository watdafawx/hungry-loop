package dev.mtop.hungryloop.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mtop.hungryloop.item.HungryLoopItem;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A quiet "alive" idle for the loop in GUIs: a sub-pixel bob and a breathing scale that never
 * exceeds 1, so it always stays inside its 16px slot. The showy spin lives in the loop's own screen.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void hungryloop$idleAnimation(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                          PoseStack pose, MultiBufferSource buffer, int light, int overlay,
                                          BakedModel model, CallbackInfo ci) {
        if (context != ItemDisplayContext.GUI || !(stack.getItem() instanceof HungryLoopItem)) {
            return;
        }
        float t = (Util.getMillis() % 3600L) / 3600f * Mth.TWO_PI;
        float scale = 0.955f + Mth.sin(t) * 0.03f;   // 0.925 .. 0.985 - always < 1
        pose.translate(0.5f, 0.5f + Mth.sin(t * 1.3f) * 0.02f, 0f);
        pose.scale(scale, scale, 1f);
        pose.translate(-0.5f, -0.5f, 0f);
    }
}
