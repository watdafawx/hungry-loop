package dev.mtop.hungryloop.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

/** Gives the loop a gentle idle rock + breathing scale wherever it's drawn flat in a GUI. */
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
        pose.translate(0.5f, 0.5f, 0f);
        pose.mulPose(Axis.ZP.rotation(Mth.sin(t) * 0.10f));
        float scale = 1f + Mth.sin(t * 1.6f) * 0.035f;
        pose.scale(scale, scale, 1f);
        pose.translate(-0.5f, -0.5f, 0f);
    }
}
