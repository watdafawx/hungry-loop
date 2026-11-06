package dev.watdafawx.hungryloop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.watdafawx.hungryloop.HungryLoop;
import dev.watdafawx.hungryloop.curio.HungryLoopCurio;
import dev.watdafawx.hungryloop.curio.LoopSlotMirror;
import dev.watdafawx.hungryloop.item.HungryLoopItem;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.Map;

/**
 * The worn loop as a small charm hanging from the belt on the right hip, modelled on the item
 * sprite: a round rose-copper band with a lavender sheen on a short bronze link, the head with its
 * pearl at the bottom. Aqua when shiny. Two loops hang side by side, tilted apart as if jostling.
 * Third person only - drawn in the body part's space by {@link LoopCurioRenderer}.
 *
 * <p>The texture is a palette atlas of flat 8x8 blocks (model boxes use box-UV): one material
 * colour per box, with the engine's face shading for depth. Each tilt is baked once and cached.
 */
public final class LoopCharm {

    private LoopCharm() {}

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HungryLoop.MODID, "textures/entity/loop_coil.png");
    private static final ResourceLocation TEXTURE_SHINY = ResourceLocation.fromNamespaceAndPath(HungryLoop.MODID, "textures/entity/loop_coil_shiny.png");

    // palette atlas: top-left of each 8x8 colour block
    private static final int[] BAND = {0, 0}, BAND_SIDE = {8, 0}, SHEEN = {16, 0}, LINK = {24, 0},
            HEAD_UV = {0, 8}, PEARL = {16, 8}, PEARL_LIGHT = {24, 8};

    /** Ring radius, band thickness, head scale (model pixels). */
    private static final float R = 1.45f;
    private static final float THICK = 0.55f;
    private static final float HEAD = 0.75f;

    /** Hang points in body space (y grows down; the belt line is y 12; the front face is z -2). */
    private static final float HANG_Y = 10.2f; // link clips onto the belt; the ring sits over the hip, not the thigh
    private static final float FRONT_Z = -2.75f;
    /** Player's right hip is -x. One loop hangs here; a pair spreads to these two. */
    private static final float SINGLE_X = -1.6f;
    private static final float[] PAIR_X = {-2.5f, 0.1f}; // clear of the arm, which covers the hip's outer edge
    private static final float[] PAIR_Z = {FRONT_Z, FRONT_Z - 0.2f};
    /** Pair tilt, degrees: leaning apart at the bottom, bands touching. */
    private static final float[] PAIR_TILT = {-14f, 13f}; // body space is y-down, so negative leans the bottom outward-left

    private static final Map<Integer, ModelPart[]> CACHE = new HashMap<>();

    /** {ring + head, pearl} baked for a tilt in whole degrees, pivoting at the link. */
    private static ModelPart[] baked(int tiltDeg) {
        return CACHE.computeIfAbsent(tiltDeg, t -> new ModelPart[]{
                LayerDefinition.create(ring(t), 32, 32).bakeRoot(),
                LayerDefinition.create(pearl(t), 32, 32).bakeRoot()});
    }

    private static CubeListBuilder box(int[] uv, float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs(uv[0], uv[1]).addBox(x, y, z, w, h, d);
    }

    private static PartDefinition pivot(MeshDefinition mesh, int tiltDeg) {
        return mesh.getRoot().addOrReplaceChild("pivot", CubeListBuilder.create(),
                PartPose.rotation(0, 0, tiltDeg * Mth.DEG_TO_RAD));
    }

    private static MeshDefinition ring(int tiltDeg) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = pivot(mesh, tiltDeg);
        float cy = 0.3f + R; // ring centre below the link
        int n = Math.round(Mth.TWO_PI * R / 0.4f);
        float a = THICK / 2, b = THICK * 0.34f, len = 0.55f;
        for (int i = 0; i < n; i++) {
            float t = Mth.TWO_PI * i / n;
            float x = Mth.cos(t) * R, y = cy + Mth.sin(t) * R;
            // Z rotation maps the box's X axis to (cos r, sin r): r = t + 90deg lays it along the circle
            float zRot = t + Mth.HALF_PI;
            p.addOrReplaceChild("a" + i, box(i % 5 == 2 ? SHEEN : BAND, -len / 2, -b, -a, len, 2 * b, 2 * a),
                    PartPose.offsetAndRotation(x, y, 0, 0, 0, zRot));
            p.addOrReplaceChild("b" + i, box(BAND_SIDE, -len / 2, -a, -b, len, 2 * a, 2 * b),
                    PartPose.offsetAndRotation(x, y, 0, 0, 0, zRot));
        }
        p.addOrReplaceChild("link", box(LINK, -0.25f, -0.7f, -0.25f, 0.5f, 1.2f, 0.5f), PartPose.ZERO);
        float hy = cy + R, k = HEAD;
        p.addOrReplaceChild("head", box(HEAD_UV, -0.9f * k, -0.8f * k, -0.5f * k, 1.8f * k, 1.6f * k, 1.0f * k),
                PartPose.offset(0, hy, 0));
        return mesh;
    }

    private static MeshDefinition pearl(int tiltDeg) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = pivot(mesh, tiltDeg);
        float hy = 0.3f + 2 * R, k = HEAD;
        p.addOrReplaceChild("pearl", box(PEARL, -0.6f * k, -0.5f * k, -0.85f * k, 1.2f * k, 1.0f * k, 0.35f * k),
                PartPose.offset(0, hy, 0));
        p.addOrReplaceChild("pearl_hi", box(PEARL_LIGHT, -0.3f * k, -0.25f * k, -1.0f * k, 0.45f * k, 0.45f * k, 0.15f * k),
                PartPose.offset(0, hy, 0));
        return mesh;
    }

    /**
     * Draw the loop worn in loop slot {@code index}. {@code pose} must already be in the body
     * part's space. With two loops worn they pair up on the hip; one hangs straight.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, int index,
                              boolean shiny) {
        boolean pair = wornCount(entity) > 1;
        int slot = Math.min(index, 1);
        float x = pair ? PAIR_X[slot] : SINGLE_X;
        float z = pair ? PAIR_Z[slot] : FRONT_Z;
        int tilt = pair ? Math.round(PAIR_TILT[slot]) : 0;

        ModelPart[] model = baked(tilt);
        pose.pushPose();
        pose.translate(x / 16f, HANG_Y / 16f, z / 16f);
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(shiny ? TEXTURE_SHINY : TEXTURE));
        model[0].render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        // the pearl breathes: brighten it on a slow pulse
        float pulse = (Mth.sin((net.minecraft.Util.getMillis() % 100_000L) / 600f) + 1f) / 2f;
        int g = Math.round(0xC8 + pulse * 0x37);
        model[1].render(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFF000000 | (g << 16) | (g << 8) | g);
        pose.popPose();
    }

    /** Visible loops worn (master eye on), so a single one hangs straight and two pair up. */
    private static int wornCount(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .flatMap(h -> h.getStacksHandler(HungryLoopCurio.LOOP_SLOT))
                .map(sh -> {
                    int n = 0;
                    for (int i = 0; i < sh.getSlots(); i++) {
                        ItemStack s = sh.getStacks().getStackInSlot(i);
                        if (s.getItem() instanceof HungryLoopItem && !LoopSlotMirror.isMirror(s)
                                && HungryLoopItem.contentsOf(s).selfRenders()) n++;
                    }
                    return n;
                }).orElse(0);
    }
}
