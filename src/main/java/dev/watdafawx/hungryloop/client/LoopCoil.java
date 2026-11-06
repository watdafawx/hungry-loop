package dev.watdafawx.hungryloop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.watdafawx.hungryloop.HungryLoop;
import dev.watdafawx.hungryloop.curio.HungryLoopCurio;
import dev.watdafawx.hungryloop.curio.LoopSlotMirror;
import dev.watdafawx.hungryloop.item.HungryLoopItem;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.Map;

/**
 * The worn loop as a 3D coil wrapped round the forearm: a copper serpent climbing from the wrist up
 * past the elbow, its head (with the loop's pearl) at the hand, its tail tapering off. Drawn in the
 * arm's own space - callers apply the arm part's transform first - so it follows every arm pose,
 * vanilla or Punchy's.
 *
 * <p>Built in code rather than from a Blockbench file so the helix can hug the arm box exactly (wide
 * and slim arms differ by a pixel). The model is baked once per (arm side, slim) and cached.
 */
public final class LoopCoil {

    private LoopCoil() {}

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HungryLoop.MODID, "textures/entity/loop_coil.png");
    private static final ResourceLocation TEXTURE_SHINY = ResourceLocation.fromNamespaceAndPath(HungryLoop.MODID, "textures/entity/loop_coil_shiny.png");

    /**
     * How the coil is laid out. Helix from the wrist (arm-local y) up the forearm, this many turns,
     * this far off the arm's surface; band thickness; head size.
     */
    private record Shape(float yWrist, float yTop, float turns, float gap, int segments, float thick, float head) {}

    /** Third person: climbs the whole forearm. */
    private static final Shape FULL = new Shape(9.6f, 2.4f, 2.25f, 0.1f, 52, 0.8f, 1f);
    /**
     * First person: the arm fills the bottom of the view, so a full coil is bulky and pulls the eye.
     * A slim band hugging the wrist, about one turn, with a small head - the loop is there without
     * covering the arm.
     */
    private static final Shape FIRST_PERSON = new Shape(10.4f, 7.6f, 1.15f, 0.05f, 30, 0.42f, 0.6f);

    private record Key(boolean left, boolean slim, boolean firstPerson) {}

    private record Baked(ModelPart body, ModelPart head) {}

    private static final Map<Key, Baked> CACHE = new HashMap<>();

    private static Baked baked(boolean left, boolean slim, boolean firstPerson) {
        Shape shape = firstPerson ? FIRST_PERSON : FULL;
        return CACHE.computeIfAbsent(new Key(left, slim, firstPerson), k -> new Baked(
                LayerDefinition.create(body(shape, k.left(), k.slim()), 32, 16).bakeRoot(),
                LayerDefinition.create(head(shape, k.left(), k.slim()), 32, 16).bakeRoot()));
    }

    /** Centre of the arm box in arm space: wide arms span x -3..1 (right) / -1..3 (left); slim one pixel narrower. */
    private static float centreX(boolean left, boolean slim) {
        float c = slim ? 0.5f : 1f;
        return left ? c : -c;
    }

    private static float halfWidth(boolean slim) {
        return slim ? 1.5f : 2f;
    }

    private static MeshDefinition body(Shape shape, boolean left, boolean slim) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float cx = centreX(left, slim);
        for (int i = 1; i < shape.segments(); i++) {
            float t = i / (float) (shape.segments() - 1);
            float[] at = helix(shape, t, cx, slim, left);
            // taper the last third into a tail
            float thick = t < 0.66f ? shape.thick() : Mth.lerp((t - 0.66f) / 0.34f, shape.thick(), shape.thick() * 0.45f);
            float len = 1.35f; // overlaps its neighbours slightly, so the coil reads as one band
            root.addOrReplaceChild("seg" + i,
                    CubeListBuilder.create().texOffs(0, (i % 4) * 4)
                            .addBox(-len / 2, -thick / 2, -thick / 2, len, thick, thick, new CubeDeformation(0.02f)),
                    PartPose.offsetAndRotation(at[0], at[1], at[2], 0, at[3], at[4]));
        }
        return mesh;
    }

    private static MeshDefinition head(Shape shape, boolean left, boolean slim) {
        MeshDefinition mesh = new MeshDefinition();
        float[] at = helix(shape, 0, centreX(left, slim), slim, left);
        float k = shape.head();
        // a blunt head with the pearl set on its outer face
        mesh.getRoot().addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(16, 0).addBox(-1.4f * k, -1.0f * k, -1.2f * k, 2.8f * k, 2.0f * k, 2.4f * k)
                        .texOffs(24, 4).addBox(-0.9f * k, -0.9f * k, 1.2f * k, 1.8f * k, 1.8f * k, 0.5f * k),
                PartPose.offsetAndRotation(at[0], at[1], at[2], 0, at[3], at[4]));
        return mesh;
    }

    /** Sleeve layer sits this far outside the arm box (the "outer layer" skin overlay). */
    private static final float SLEEVE = 0.25f;
    /** Superellipse exponent: 2 is a circle, higher hugs a square. */
    private static final float SQUARENESS = 6f;

    /**
     * {x, y, z, yRot, zRot} for parameter t (0 = wrist, 1 = tail). The path is a rounded square round
     * the arm box (plus sleeve), not a circle: the arm is square, so a circle tight enough to sit on
     * its flat sides cuts through its corners - the band clipped into the hand in first person.
     */
    private static float[] helix(Shape shape, float t, float cx, boolean slim, boolean left) {
        float half = halfWidth(slim) + SLEEVE + shape.gap() + shape.thick() / 2;
        float dir = left ? -1f : 1f; // mirror the winding on the other arm
        float theta = dir * t * shape.turns() * Mth.TWO_PI;
        float[] p = squircle(theta, half);
        float[] q = squircle(theta + dir * 0.01f, half);
        float x = cx + p[0];
        float z = p[1];
        float y = Mth.lerp(t, shape.yWrist(), shape.yTop());
        // ModelPart's Y rotation maps the cube's X axis to (cos a, 0, -sin a); lay it along the path's
        // tangent (dx, dz): a = atan2(-dz, dx).
        float dx = q[0] - p[0], dz = q[1] - p[1];
        float yRot = (float) Math.atan2(-dz, dx);
        float perimeter = 8 * half * 0.92f; // close to a square's perimeter
        float climb = (shape.yTop() - shape.yWrist()) / (shape.turns() * perimeter);
        float zRot = (float) Math.atan(climb) * dir; // tilt up along the climb
        return new float[]{x, y, z, yRot, zRot};
    }

    /** A point on a superellipse of half-size {@code half} at angle {@code theta}. */
    private static float[] squircle(float theta, float half) {
        float c = Mth.cos(theta), s = Mth.sin(theta);
        float e = 2f / SQUARENESS;
        return new float[]{
                Math.signum(c) * (float) Math.pow(Math.abs(c), e) * half,
                Math.signum(s) * (float) Math.pow(Math.abs(s), e) * half};
    }

    /**
     * Draw the coil. {@code pose} must already be in the arm's space (after the arm part's
     * {@code translateAndRotate}).
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, boolean leftArm, boolean slim,
                              boolean shiny, boolean firstPerson) {
        Baked model = baked(leftArm, slim, firstPerson);
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(shiny ? TEXTURE_SHINY : TEXTURE));
        model.body().render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        // the pearl breathes: brighten the head on a slow pulse
        float pulse = (Mth.sin((net.minecraft.Util.getMillis() % 100_000L) / 600f) + 1f) / 2f;
        int g = Math.round(0xC8 + pulse * 0x37);
        model.head().render(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFF000000 | (g << 16) | (g << 8) | g);
    }

    // --- which loop goes on which arm -------------------------------------------------------------

    /**
     * Loop slot 0 goes on the main arm, slot 1 on the other. Returns that loop for {@code arm}, or
     * EMPTY if none is worn there or its master eye hides it.
     */
    public static ItemStack loopFor(LivingEntity entity, HumanoidArm arm) {
        int index = arm == entity.getMainArm() ? 0 : 1;
        return CuriosApi.getCuriosInventory(entity)
                .flatMap(h -> h.getStacksHandler(HungryLoopCurio.LOOP_SLOT))
                .map(sh -> index < sh.getSlots() ? sh.getStacks().getStackInSlot(index) : ItemStack.EMPTY)
                .filter(s -> s.getItem() instanceof HungryLoopItem && !LoopSlotMirror.isMirror(s)
                        && HungryLoopItem.contentsOf(s).selfRenders())
                .orElse(ItemStack.EMPTY);
    }

    public static boolean isSlim(LivingEntity entity) {
        return entity instanceof AbstractClientPlayer p && p.getSkin().model() == PlayerSkin.Model.SLIM;
    }
}
