package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.ChlorineCloudEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/** Five chlorine billboards aging through eight increasingly unhealthy frames. */
public final class ChlorineCloudRenderer extends EntityRenderer<ChlorineCloudEntity> {
    private static final ResourceLocation[] FRAMES = new ResourceLocation[8];

    static {
        for (int i = 0; i < FRAMES.length; i++) {
            FRAMES[i] = ResourceLocation.fromNamespaceAndPath(
                    HbmNtm.MOD_ID, "textures/item/chlorine" + (i + 1) + ".png");
        }
    }

    public ChlorineCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(ChlorineCloudEntity cloud, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(frame(cloud)));
        Random shadeRandom = new Random(cloud.hashCode());
        Random placementRandom = new Random(100L);

        poses.pushPose();
        poses.scale(3.75F, 3.75F, 3.75F);
        for (int i = 0; i < 5; i++) {
            float shade = 1.0F - shadeRandom.nextInt(10) * 0.05F;
            double x = (placementRandom.nextGaussian() - 1.0D) * 0.15D;
            double y = (placementRandom.nextGaussian() - 1.0D) * 0.15D;
            double z = (placementRandom.nextGaussian() - 1.0D) * 0.15D;
            float size = (float) (placementRandom.nextDouble() * 0.5D + 0.25D);

            poses.pushPose();
            poses.translate(x, y, z);
            poses.scale(size, size, size);
            poses.mulPose(entityRenderDispatcher.cameraOrientation());
            PoseStack.Pose pose = poses.last();
            vertex(consumer, pose, -0.5F, -0.25F, 0.0F, 1.0F, shade, packedLight);
            vertex(consumer, pose, 0.5F, -0.25F, 1.0F, 1.0F, shade, packedLight);
            vertex(consumer, pose, 0.5F, 0.75F, 1.0F, 0.0F, shade, packedLight);
            vertex(consumer, pose, -0.5F, 0.75F, 0.0F, 0.0F, shade, packedLight);
            poses.popPose();
        }
        poses.popPose();
        super.render(cloud, yaw, partialTick, poses, buffers, packedLight);
    }

    private static ResourceLocation frame(ChlorineCloudEntity cloud) {
        if (cloud.maxAge() <= 0) return FRAMES[0];
        int bucket = Math.max(cloud.maxAge() / 8, 1);
        int frame = Math.min(cloud.particleAge() / bucket, 7);
        return FRAMES[Math.max(frame, 0)];
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y,
                               float u, float v, float shade, int packedLight) {
        consumer.addVertex(pose, x, y, 0.0F).setColor(shade, shade, shade, 1.0F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(ChlorineCloudEntity entity) {
        return frame(entity);
    }
}
