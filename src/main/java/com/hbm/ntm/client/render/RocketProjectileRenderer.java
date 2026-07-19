package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.entity.RocketProjectileEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Set;

/** Launcher rocket followed by an unnecessarily bright tapered exhaust. */
public final class RocketProjectileRenderer extends EntityRenderer<RocketProjectileEntity> {
    private static final ResourceLocation MODEL = id("models/weapons/panzerschreck.obj");
    private static final ResourceLocation TEXTURE = id("textures/models/weapons/panzerschreck.png");
    private static final ResourceLocation MISSILE_MODEL =
            id("models/weapons/missile_launcher.obj");
    private static final ResourceLocation MISSILE_TEXTURE =
            id("textures/models/weapons/missile_launcher.png");
    private static final ResourceLocation BLANK = id("textures/particle/particle_base.png");
    private static final Set<String> GROUPS = Set.of("Rocket");
    private static final RenderType TRAIL = RenderType.create(
            "hbm_rpzb_trail", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            128, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    private EnvsuitMesh mesh;
    private EnvsuitMesh missileMesh;

    public RocketProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(RocketProjectileEntity rocket, float yaw, float partialTick,
                       PoseStack poses, MultiBufferSource buffers, int light) {
        Vec3 direction = rocket.direction();
        float horizontal = Mth.sqrt((float) (direction.x * direction.x + direction.z * direction.z));
        float authoredYaw = (float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
        float authoredPitch = (float) (Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG);

        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(authoredYaw - 90.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(authoredPitch + 180.0F));
        if (rocket.flightMode() == RocketProjectileEntity.FlightMode.MISSILE_LAUNCHER) {
            poses.scale(0.25F, 0.25F, 0.25F);
            poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            poses.translate(0.0D, -1.0D, -4.5D);
            missileMesh().render("Missile", poses.last(),
                    buffers.getBuffer(RenderType.entityCutout(MISSILE_TEXTURE)),
                    1.0F, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, -1);
        } else {
            poses.scale(0.125F, 0.125F, 0.125F);
            poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            poses.translate(0.0D, 0.0D, 3.5D);
            mesh().render("Rocket", poses.last(), buffers.getBuffer(RenderType.entityCutout(TEXTURE)),
                    1.0F, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, -1);
        }
        poses.popPose();

        float length = rocket.interpolatedSpeed(partialTick) * 2.0F;
        if (length > 0.0F) renderTrail(poses, buffers, direction, length);
        super.render(rocket, yaw, partialTick, poses, buffers, light);
    }

    private static void renderTrail(PoseStack poses, MultiBufferSource buffers,
                                    Vec3 directionVector, float length) {
        // Local +X points behind the rocket; offset and taper both run opposite travel.
        Vector3f direction = directionVector.toVector3f().normalize().negate();
        Vector3f reference = Math.abs(direction.y()) > 0.95F
                ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f right = direction.cross(reference, new Vector3f()).normalize();
        Vector3f up = right.cross(direction, new Vector3f()).normalize();
        Vector3f front = new Vector3f(direction).mul(0.375F);
        Vector3f back = new Vector3f(front).add(new Vector3f(direction).mul(length));
        Vector3f frontRight = new Vector3f(right).mul(0.03125F);
        Vector3f frontUp = new Vector3f(up).mul(0.03125F);
        Vector3f backRight = new Vector3f(right).mul(0.03125F * 0.25F);
        Vector3f backUp = new Vector3f(up).mul(0.03125F * 0.25F);

        Vector3f fpp = new Vector3f(front).add(frontRight).add(frontUp);
        Vector3f fpn = new Vector3f(front).add(frontRight).sub(frontUp);
        Vector3f fnp = new Vector3f(front).sub(frontRight).add(frontUp);
        Vector3f fnn = new Vector3f(front).sub(frontRight).sub(frontUp);
        Vector3f bpp = new Vector3f(back).add(backRight).add(backUp);
        Vector3f bpn = new Vector3f(back).add(backRight).sub(backUp);
        Vector3f bnp = new Vector3f(back).sub(backRight).add(backUp);
        Vector3f bnn = new Vector3f(back).sub(backRight).sub(backUp);

        VertexConsumer consumer = buffers.getBuffer(TRAIL);
        PoseStack.Pose pose = poses.last();
        int light = 0xFFFFF2A7;
        int dark = 0xFF808080;
        quad(consumer, pose, bpp, bnp, fnp, fpp, dark, light);
        quad(consumer, pose, bnn, bpn, fpn, fnn, dark, light);
        quad(consumer, pose, bnp, bnn, fnn, fnp, dark, light);
        quad(consumer, pose, bpn, bpp, fpp, fpn, dark, light);
        quad(consumer, pose, bpp, bpn, bnn, bnp, dark, dark);
        quad(consumer, pose, fpp, fnp, fnn, fpn, light, light);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                             int firstColor, int secondColor) {
        vertex(consumer, pose, a, firstColor);
        vertex(consumer, pose, b, firstColor);
        vertex(consumer, pose, c, secondColor);
        vertex(consumer, pose, d, secondColor);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               Vector3f point, int color) {
        consumer.addVertex(pose, point.x(), point.y(), point.z()).setColor(color);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(),
                MODEL, GROUPS, "Panzerschreck Rocket");
        return mesh;
    }

    private EnvsuitMesh missileMesh() {
        if (missileMesh == null) {
            missileMesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(),
                    MISSILE_MODEL, Set.of("Missile"), "Missile Launcher Projectile");
        }
        return missileMesh;
    }

    @Override public ResourceLocation getTextureLocation(RocketProjectileEntity entity) { return BLANK; }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }
}
