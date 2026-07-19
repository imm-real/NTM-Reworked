package com.hbm.ntm.client.render;

import com.hbm.ntm.entity.ShredderSubmunitionEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Small camera-facing additive plasma glow for the shredder submunition. The old renderer drew cloned
 * MK4 bullet with the base-load tracer; this port draws a compact cyan/blue billboard, which is sufficient
 * for a fast, short-lived plasma bolt. The old BeamPronter-style tracer is not implemented yet.
 */
public final class ShredderSubmunitionRenderer extends EntityRenderer<ShredderSubmunitionEntity> {
    private static final RenderType TYPE = RenderType.create(
            "hbm_shredder_submunition", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256,
            false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    public ShredderSubmunitionRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(ShredderSubmunitionEntity sub, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        Camera camera = entityRenderDispatcher.camera;
        poses.pushPose();
        poses.mulPose(camera.rotation());
        poses.mulPose(Axis.YP.rotationDegrees(180.0F));
        VertexConsumer consumer = buffers.getBuffer(TYPE);
        PoseStack.Pose pose = poses.last();
        float s = 0.12F;
        vertex(consumer, pose, -s, -s);
        vertex(consumer, pose, -s, s);
        vertex(consumer, pose, s, s);
        vertex(consumer, pose, s, -s);
        poses.popPose();
        super.render(sub, yaw, partialTick, poses, buffers, packedLight);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y) {
        consumer.addVertex(pose, x, y, 0.0F).setColor(0.5F, 0.7F, 1.0F, 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(ShredderSubmunitionEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
