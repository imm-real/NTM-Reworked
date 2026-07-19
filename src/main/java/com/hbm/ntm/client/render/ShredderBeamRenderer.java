package com.hbm.ntm.client.render;

import com.hbm.ntm.entity.ShredderBeamEntity;
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

/**
 * Straight additive shredder beam in dark cyan-blue. The missing BeamPronter waveform
 * would make it noisy; this one settles for fading over five ticks.
 */
public final class ShredderBeamRenderer extends EntityRenderer<ShredderBeamEntity> {
    private static final RenderType TYPE = RenderType.create(
            "hbm_shredder_beam", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256,
            false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    // RENDER_LASER_CYAN colour: renderStandardLaser(0x15, 0x15, 0x80).
    private static final float RED = 0x15 / 255.0F;
    private static final float GREEN = 0x15 / 255.0F;
    private static final float BLUE = 0x80 / 255.0F;
    private static final int LIFE = 5;

    public ShredderBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(ShredderBeamEntity beam, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        float age = beam.tickCount + partialTick;
        float fade = Math.max(0.0F, 1.0F - age / LIFE);
        if (fade <= 0.0F) return;
        float length = beam.beamLength();
        if (length <= 0.0F) return;

        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(beam.getYRot()));
        poses.mulPose(Axis.XN.rotationDegrees(beam.getXRot()));
        VertexConsumer consumer = buffers.getBuffer(TYPE);
        PoseStack.Pose pose = poses.last();
        float radius = 0.05F;
        for (int layer = 0; layer <= 4; layer++) {
            float offset = radius * layer / 4.0F;
            float intensity = fade * Math.max(0.15F, 1.0F - layer / 4.0F);
            prism(consumer, pose, offset, length, intensity);
        }
        poses.popPose();
        super.render(beam, yaw, partialTick, poses, buffers, packedLight);
    }

    private static void prism(VertexConsumer consumer, PoseStack.Pose pose,
                              float radius, float length, float intensity) {
        quad(consumer, pose, -radius,-radius,0, radius,-radius,0,
                radius,-radius,length, -radius,-radius,length, intensity);
        quad(consumer, pose, radius,-radius,0, radius,radius,0,
                radius,radius,length, radius,-radius,length, intensity);
        quad(consumer, pose, radius,radius,0, -radius,radius,0,
                -radius,radius,length, radius,radius,length, intensity);
        quad(consumer, pose, -radius,radius,0, -radius,-radius,0,
                -radius,-radius,length, -radius,radius,length, intensity);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float cx,float cy,float cz, float dx,float dy,float dz, float intensity) {
        vertex(consumer, pose, ax,ay,az,intensity);
        vertex(consumer, pose, bx,by,bz,intensity);
        vertex(consumer, pose, cx,cy,cz,intensity);
        vertex(consumer, pose, dx,dy,dz,intensity);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float intensity) {
        consumer.addVertex(pose, x, y, z).setColor(RED, GREEN, BLUE, intensity);
    }

    @Override
    public ResourceLocation getTextureLocation(ShredderBeamEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
