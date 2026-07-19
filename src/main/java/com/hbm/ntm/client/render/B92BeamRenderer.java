package com.hbm.ntm.client.render;

import com.hbm.ntm.entity.B92BeamEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Several additive blue-white squares pretending to be a beam. */
public final class B92BeamRenderer extends EntityRenderer<B92BeamEntity> {
    private static final RenderType TYPE = RenderType.create(
            "hbm_b92_beam", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256,
            false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    public B92BeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(B92BeamEntity beam, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(beam.getYRot()));
        poses.mulPose(Axis.XN.rotationDegrees(beam.getXRot()));
        VertexConsumer consumer = buffers.getBuffer(TYPE);
        PoseStack.Pose pose = poses.last();
        float radius = 0.175F;
        for (int layer = 0; layer <= 8; layer++) {
            float offset = radius * layer / 8.0F;
            float color = Math.max(0.0F, 1.0F - offset * 8.333F);
            prism(consumer, pose, offset, 2.0F, color);
        }
        poses.popPose();
        super.render(beam, yaw, partialTick, poses, buffers, packedLight);
    }

    private static void prism(VertexConsumer consumer, PoseStack.Pose pose,
                              float radius, float length, float color) {
        quad(consumer, pose, -radius,-radius,0, radius,-radius,0,
                radius,-radius,length, -radius,-radius,length, color);
        quad(consumer, pose, radius,-radius,0, radius,radius,0,
                radius,radius,length, radius,-radius,length, color);
        quad(consumer, pose, radius,radius,0, -radius,radius,0,
                -radius,radius,length, radius,radius,length, color);
        quad(consumer, pose, -radius,radius,0, -radius,-radius,0,
                -radius,-radius,length, -radius,radius,length, color);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float cx,float cy,float cz, float dx,float dy,float dz, float color) {
        vertex(consumer, pose, ax,ay,az,color);
        vertex(consumer, pose, bx,by,bz,color);
        vertex(consumer, pose, cx,cy,cz,color);
        vertex(consumer, pose, dx,dy,dz,color);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float color) {
        consumer.addVertex(pose, x, y, z).setColor(color, color, 1.0F, 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(B92BeamEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
