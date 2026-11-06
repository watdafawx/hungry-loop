package dev.watdafawx.hungryloop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws the worn loop as a charm hanging from the belt ({@link LoopCharm}); two loops pair up on
 * the hip. Follows the body's sneak pose. Curios only calls this while the loop's slot renders, which
 * is the loop screen's master eye.
 */
public final class LoopCurioRenderer implements ICurioRenderer {


    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
            PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(parent.getModel() instanceof HumanoidModel<?> model)) return;
        LivingEntity entity = slotContext.entity();
        pose.pushPose();
        ICurioRenderer.translateIfSneaking(pose, entity);
        ICurioRenderer.rotateIfSneaking(pose, entity);
        model.body.translateAndRotate(pose);
        LoopCharm.render(pose, buffers, light, entity, slotContext.index(),
                dev.watdafawx.hungryloop.item.HungryLoopItem.contentsOf(stack).shiny());
        pose.popPose();
    }
}
