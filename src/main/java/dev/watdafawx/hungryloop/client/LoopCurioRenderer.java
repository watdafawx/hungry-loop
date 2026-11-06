package dev.watdafawx.hungryloop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws the worn loop on the wearer's main hand: its (animated) item sprite, small, on the outside
 * of the hand where a ring on the middle finger would show. It follows the arm's swing and the
 * sneak pose. Curios only calls this while the loop's slot is set to render, which is the loop
 * screen's master eye, so hiding the loop there hides this too.
 *
 * <p>Registered in {@code HungryLoopClient}; with Accessories' compat layer the Curios renderer
 * registry is bridged, so it draws there as well.
 */
public final class LoopCurioRenderer implements ICurioRenderer {

    /** Where the ring sits on the arm, in model pixels from the shoulder pivot. */
    private static final float HAND_Y = 9.2f / 16f;
    private static final float OUTSIDE_X = 3.05f / 16f;
    private static final float SCALE = 0.34f;
    private static final float FIRST_PERSON_SCALE = 0.2f;
    private static final float FIRST_PERSON_HAND_Y = 8.0f / 16f;

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
            PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(parent.getModel() instanceof HumanoidModel<?> model)) return;
        LivingEntity entity = slotContext.entity();
        boolean right = entity.getMainArm() == HumanoidArm.RIGHT;

        pose.pushPose();
        ICurioRenderer.translateIfSneaking(pose, entity);
        ICurioRenderer.rotateIfSneaking(pose, entity);
        (right ? model.rightArm : model.leftArm).translateAndRotate(pose);
        placeOnHand(pose, right, false);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
    }

    /**
     * From an arm's own transform to the ring's spot: the arm box spans x -3..1 (right) / -1..3
     * (left), so the ring goes on its outer face near the hand, sprite facing outward. Shared with
     * the first-person arm ({@link LoopFirstPerson}).
     */
    static void placeOnHand(PoseStack pose, boolean right, boolean firstPerson) {
        // the first-person arm is drawn magnified, right in front of the camera: smaller, and up on
        // the knuckles rather than past the fist
        float scale = firstPerson ? FIRST_PERSON_SCALE : SCALE;
        pose.translate(right ? -OUTSIDE_X : OUTSIDE_X, firstPerson ? FIRST_PERSON_HAND_Y : HAND_Y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(right ? 90 : -90)); // sprite faces outward
        pose.mulPose(Axis.ZP.rotationDegrees(180));              // model space is upside down
        pose.scale(scale, scale, scale);
    }
}
