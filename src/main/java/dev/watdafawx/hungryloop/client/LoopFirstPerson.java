package dev.watdafawx.hungryloop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.watdafawx.hungryloop.HungryLoop;
import dev.watdafawx.hungryloop.curio.LoopSlotMirror;
import dev.watdafawx.hungryloop.item.HungryLoopItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * The worn loop on your own hand in first person (empty main hand). Curios only draws on the
 * third-person body, so this takes over the first-person arm: draws the arm as vanilla would, then
 * the ring on it with the same arm transform as {@link LoopCurioRenderer}, and cancels the vanilla
 * draw. Respects the loop's master eye (hidden loop, no ring).
 */
@EventBusSubscriber(modid = HungryLoop.MODID, value = Dist.CLIENT)
public final class LoopFirstPerson {

    private LoopFirstPerson() {}

    /**
     * renderRightHand/renderLeftHand fire RenderArmEvent themselves; while we call them to draw the
     * arm, let that inner event through untouched.
     */
    private static boolean drawingArm;

    @SubscribeEvent
    static void onRenderArm(RenderArmEvent event) {
        if (drawingArm) return;
        AbstractClientPlayer player = event.getPlayer();
        if (event.getArm() != player.getMainArm()) return;
        ItemStack loop = wornLoop(player);
        if (loop.isEmpty() || !HungryLoopItem.contentsOf(loop).selfRenders()) return;
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;

        PoseStack pose = event.getPoseStack();
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        drawingArm = true;
        try {
            if (right) {
                renderer.renderRightHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
            } else {
                renderer.renderLeftHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
            }
        } finally {
            drawingArm = false;
        }

        var model = renderer.getModel();
        pose.pushPose();
        (right ? model.rightArm : model.leftArm).translateAndRotate(pose);
        LoopCurioRenderer.placeOnHand(pose, right, true);
        Minecraft.getInstance().getItemRenderer().renderStatic(loop, ItemDisplayContext.FIXED, event.getPackedLight(),
                OverlayTexture.NO_OVERLAY, pose, event.getMultiBufferSource(), player.level(), player.getId());
        pose.popPose();
        event.setCanceled(true);
    }

    private static ItemStack wornLoop(AbstractClientPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(h -> h.findFirstCurio(s -> s.getItem() instanceof HungryLoopItem && !LoopSlotMirror.isMirror(s)))
                .map(SlotResult::stack).orElse(ItemStack.EMPTY);
    }
}
