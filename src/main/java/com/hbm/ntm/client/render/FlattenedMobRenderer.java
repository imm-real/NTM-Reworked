package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.FlattenedMobEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Draws the punched mob as a paper-thin 2D portrait inside a transparent rectangle. */
public final class FlattenedMobRenderer extends EntityRenderer<FlattenedMobEntity> {
    private static final ResourceLocation PANE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/entity/flattened_pane.png");
    /** How much mob is left along the pane normal after the punch. */
    private static final float FLATTENING = 0.02F;
    private static final float MARGIN = 0.0625F;
    /** Half-thickness of the glass slab the portrait is sealed inside. */
    private static final float SLAB_HALF = 0.05F;

    public FlattenedMobRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(FlattenedMobEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        poses.pushPose();
        // Local +Z now points at whoever threw the punch; the portrait faces its audience.
        poses.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));

        Entity victim = entity.displayEntity();
        if (victim != null) {
            poses.pushPose();
            poses.scale(1.0F, 1.0F, FLATTENING);
            entityRenderDispatcher.getRenderer(victim).render(victim, 0.0F, 0.0F, poses, buffers, light);
            poses.popPose();
        }

        float half = entity.getBbWidth() * 0.5F + MARGIN;
        float top = entity.getBbHeight() + MARGIN;
        VertexConsumer pane = buffers.getBuffer(RenderType.entityTranslucent(PANE_TEXTURE));
        slab(pane, poses.last(), -half, -MARGIN, -SLAB_HALF, half, top, SLAB_HALF, light);

        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    /** One closed thin glass box: front, back, and the four edges sealing them together. */
    private static void slab(VertexConsumer consumer, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        face(consumer, pose, light, 0.0F, 0.0F, 1.0F,
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(consumer, pose, light, 0.0F, 0.0F, -1.0F,
                x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        face(consumer, pose, light, -1.0F, 0.0F, 0.0F,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(consumer, pose, light, 1.0F, 0.0F, 0.0F,
                x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        face(consumer, pose, light, 0.0F, 1.0F, 0.0F,
                x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        face(consumer, pose, light, 0.0F, -1.0F, 0.0F,
                x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    private static void face(VertexConsumer consumer, PoseStack.Pose pose, int light,
                             float normalX, float normalY, float normalZ, float... corners) {
        float[][] uv = {{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}, {0.0F, 0.0F}};
        for (int index = 0; index < 4; index++) {
            consumer.addVertex(pose, corners[index * 3], corners[index * 3 + 1], corners[index * 3 + 2])
                    .setColor(255, 255, 255, 255)
                    .setUv(uv[index][0], uv[index][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, normalX, normalY, normalZ);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(FlattenedMobEntity entity) {
        return PANE_TEXTURE;
    }
}
