package dev.watdafawx.hungryloop.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.watdafawx.hungryloop.client.LoopCoil;
import dev.watdafawx.hungryloop.item.HungryLoopItem;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The loop's coil on the first-person arm. Drawn right after the arm part itself, inside
 * {@code renderHand}, so it uses exactly the pose the arm was just drawn with - including poses
 * another mod (Punchy) set up for that draw - instead of re-deriving one afterwards.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerHandLoopMixin {

    @Inject(method = "renderHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
            ordinal = 0, shift = At.Shift.AFTER))
    private void hungryloop$coilOnArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                      ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        PlayerRenderer self = (PlayerRenderer) (Object) this;
        boolean left = arm == self.getModel().leftArm;
        ItemStack loop = LoopCoil.loopFor(player, left ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
        if (loop.isEmpty()) return;
        pose.pushPose();
        arm.translateAndRotate(pose);
        LoopCoil.render(pose, buffers, light, left, LoopCoil.isSlim(player), HungryLoopItem.contentsOf(loop).shiny(), true);
        pose.popPose();
    }
}
