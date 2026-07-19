package com.hbm.ntm.client.render;

import com.hbm.ntm.entity.PowerFistBeamEntity;
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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** RenderBeam2/3's untextured, additive, four-block Power Fist beams. */
public final class PowerFistBeamRenderer extends EntityRenderer<PowerFistBeamEntity> {
    private static final RenderType TYPE = RenderType.create(
            "hbm_power_fist_beam", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512,
            false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    private final Kind kind;

    public PowerFistBeamRenderer(EntityRendererProvider.Context context, Kind kind) {
        super(context);
        this.kind = kind;
        shadowRadius = 0.0F;
    }

    @Override
    public void render(PowerFistBeamEntity beam, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(beam.getYRot()));
        poses.mulPose(Axis.XN.rotationDegrees(beam.getXRot()));
        VertexConsumer consumer = buffers.getBuffer(TYPE);
        PoseStack.Pose pose = poses.last();
        float radius = 0.12F;
        for (int layer = 0; layer <= 8; layer++) {
            float offset = radius * layer / 8.0F;
            float fade = Math.max(0.0F, 1.0F - offset * 8.333F);
            float green = kind == Kind.LASER ? 1.0F : fade;
            prism(consumer, pose, offset, 4.0F, 1.0F, green, 1.0F);
        }
        poses.popPose();
        super.render(beam, yaw, partialTick, poses, buffers, packedLight);
    }

    private static void prism(VertexConsumer consumer, PoseStack.Pose pose, float radius, float length,
                              float red, float green, float blue) {
        quad(consumer, pose, radius, -radius, 0, radius, radius, 0,
                radius, radius, length, radius, -radius, length, red, green, blue);
        quad(consumer, pose, -radius, -radius, 0, radius, -radius, 0,
                radius, -radius, length, -radius, -radius, length, red, green, blue);
        quad(consumer, pose, -radius, radius, 0, -radius, -radius, 0,
                -radius, -radius, length, -radius, radius, length, red, green, blue);
        quad(consumer, pose, radius, radius, 0, -radius, radius, 0,
                -radius, radius, length, radius, radius, length, red, green, blue);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float red, float green, float blue) {
        vertex(consumer, pose, ax, ay, az, red, green, blue);
        vertex(consumer, pose, bx, by, bz, red, green, blue);
        vertex(consumer, pose, cx, cy, cz, red, green, blue);
        vertex(consumer, pose, dx, dy, dz, red, green, blue);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                               float red, float green, float blue) {
        consumer.addVertex(pose, x, y, z).setColor(red, green, blue, 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(PowerFistBeamEntity entity) {
        // The beam RenderType is untextured; return a guaranteed resource for
        // renderer infrastructure and diagnostics rather than a removed path.
        return TextureAtlas.LOCATION_BLOCKS;
    }

    public enum Kind {
        LASER,
        MINER
    }
}
