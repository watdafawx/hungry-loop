package dev.watdafawx.hungryloop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws the worn loop as a coil round the wearer's forearm ({@link LoopCoil}): loop slot 0 on the
 * main arm, slot 1 on the other. It follows the arm's swing and the sneak pose. Curios only calls this while the loop's slot is set to render, which is the loop
 * screen's master eye, so hiding the loop there hides this too.
 *
 * <p>Registered in {@code HungryLoopClient}; with Accessories' compat layer the Curios renderer
 * registry is bridged, so it draws there as well.
 */
public final class LoopCurioRenderer implements ICurioRenderer {


    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
            PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(parent.getModel() instanceof HumanoidModel<?> model)) return;
        LivingEntity entity = slotContext.entity();
        // loop slot 0 coils the main arm, slot 1 the other one
        HumanoidArm arm = slotContext.index() == 0 ? entity.getMainArm() : entity.getMainArm().getOpposite();
        boolean left = arm == HumanoidArm.LEFT;

        pose.pushPose();
        ICurioRenderer.translateIfSneaking(pose, entity);
        ICurioRenderer.rotateIfSneaking(pose, entity);
        (left ? model.leftArm : model.rightArm).translateAndRotate(pose);
        LoopCoil.render(pose, buffers, light, left, LoopCoil.isSlim(entity),
                dev.watdafawx.hungryloop.item.HungryLoopItem.contentsOf(stack).shiny(), false);
        pose.popPose();
    }
}
