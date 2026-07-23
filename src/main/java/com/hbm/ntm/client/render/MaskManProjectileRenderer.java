package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.MaskManProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class MaskManProjectileRenderer extends EntityRenderer<MaskManProjectileEntity> {
    private static final ResourceLocation BLANK = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/particle/particle_base.png");

    public MaskManProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(MaskManProjectileEntity projectile, float yaw, float partialTick,
                       PoseStack poses, MultiBufferSource buffers, int packedLight) {
        Vec3 direction = projectile.getDeltaMovement();
        if (direction.lengthSqr() < 1.0E-8D) direction = new Vec3(1.0D, 0.0D, 0.0D);
        direction = direction.normalize();
        float horizontal = Mth.sqrt((float) (direction.x * direction.x + direction.z * direction.z));
        float authoredYaw = (float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
        float authoredPitch = (float) (Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG);

        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(authoredYaw - 90.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(authoredPitch));
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        switch (projectile.kind()) {
            case ORB -> cube(consumer, poses.last(), 0.45F, 0.5F, 0.0F, 0.0F, 0.65F);
            case BOLT -> dart(consumer, poses.last(), 0.25F, 0.0F, 0.75F);
            case TRACER -> dart(consumer, poses.last(), 1.0F, 1.0F, 0.0F);
            case ROCKET -> prism(consumer, poses.last(), 0.8F, 0.18F,
                    0.25F, 0.25F, 0.25F, 1.0F);
            case METEOR -> cube(consumer, poses.last(), 0.65F, 1.0F, 0.3F, 0.0F, 1.0F);
        }
        poses.popPose();
        super.render(projectile, yaw, partialTick, poses, buffers, packedLight);
    }

    private static void dart(VertexConsumer out, PoseStack.Pose pose, float red, float green, float blue) {
        float length = 1.5F;
        float core = 0.1F;
        quad(out, pose, length, 0.0F, 0.0F, 1.0F,
                0.45F, -core, -core, 0.0F,
                0.45F, core, -core, 0.0F,
                0.45F, core, core, 0.0F,
                red, green, blue);
        quad(out, pose, length, 0.0F, 0.0F, 1.0F,
                0.45F, core, core, 0.0F,
                0.45F, -core, core, 0.0F,
                0.45F, -core, -core, 0.0F,
                red, green, blue);
        prism(out, pose, 0.8F, core, red, green, blue, 0.8F);
    }

    private static void prism(VertexConsumer out, PoseStack.Pose pose, float length, float radius,
                              float red, float green, float blue, float alpha) {
        quadSolid(out, pose, 0.0F, -radius, -radius, length, -radius, -radius,
                length, radius, -radius, 0.0F, radius, -radius, red, green, blue, alpha);
        quadSolid(out, pose, 0.0F, radius, radius, length, radius, radius,
                length, -radius, radius, 0.0F, -radius, radius, red, green, blue, alpha);
        quadSolid(out, pose, 0.0F, -radius, radius, length, -radius, radius,
                length, -radius, -radius, 0.0F, -radius, -radius, red, green, blue, alpha);
        quadSolid(out, pose, 0.0F, radius, -radius, length, radius, -radius,
                length, radius, radius, 0.0F, radius, radius, red, green, blue, alpha);
    }

    private static void cube(VertexConsumer out, PoseStack.Pose pose, float radius,
                             float red, float green, float blue, float alpha) {
        prism(out, pose, radius * 2.0F, radius, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose,
                             float x1, float y1, float z1, float a1,
                             float x2, float y2, float z2, float a2,
                             float x3, float y3, float z3, float a3,
                             float x4, float y4, float z4, float a4,
                             float red, float green, float blue) {
        vertex(out, pose, x1, y1, z1, red, green, blue, a1);
        vertex(out, pose, x2, y2, z2, red, green, blue, a2);
        vertex(out, pose, x3, y3, z3, red, green, blue, a3);
        vertex(out, pose, x4, y4, z4, red, green, blue, a4);
    }

    private static void quadSolid(VertexConsumer out, PoseStack.Pose pose,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float x3, float y3, float z3,
                                  float x4, float y4, float z4,
                                  float red, float green, float blue, float alpha) {
        quad(out, pose, x1, y1, z1, alpha, x2, y2, z2, alpha,
                x3, y3, z3, alpha, x4, y4, z4, alpha, red, green, blue);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose,
                               float x, float y, float z,
                               float red, float green, float blue, float alpha) {
        out.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(MaskManProjectileEntity entity) {
        return BLANK;
    }
}
