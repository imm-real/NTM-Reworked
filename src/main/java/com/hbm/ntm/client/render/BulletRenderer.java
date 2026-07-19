package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class BulletRenderer extends EntityRenderer<BulletEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/particle/particle_base.png");
    private static final RenderType TYPE = RenderType.create(
            "hbm_standard_bullet",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_LIGHTMAP_SHADER)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false)
    );

    public BulletRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(BulletEntity bullet, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        double length = bullet.tracerLength(partialTick);
        if (length <= 0.0D) return;

        poses.pushPose();
        float renderYaw = Mth.lerp(partialTick, bullet.yRotO, bullet.getYRot());
        float renderPitch = Mth.lerp(partialTick, bullet.xRotO, bullet.getXRot());
        poses.mulPose(Axis.YP.rotationDegrees(renderYaw - 90.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(renderPitch + 180.0F));

        float widthFront = 0.03125F;
        float widthBack = widthFront * 0.25F;
        int dark = bullet.tracerDarkColor();
        int light = bullet.tracerLightColor();
        int blockLight = bullet.tracerFullbright() ? LightTexture.FULL_BRIGHT : packedLight;
        VertexConsumer consumer = buffers.getBuffer(TYPE);
        PoseStack.Pose pose = poses.last();
        vertex(consumer, pose, length, widthBack, -widthBack, dark, blockLight);
        vertex(consumer, pose, length, widthBack, widthBack, dark, blockLight);
        vertex(consumer, pose, 0.0D, widthFront, widthFront, light, blockLight);
        vertex(consumer, pose, 0.0D, widthFront, -widthFront, light, blockLight);

        vertex(consumer, pose, length, -widthBack, -widthBack, dark, blockLight);
        vertex(consumer, pose, length, -widthBack, widthBack, dark, blockLight);
        vertex(consumer, pose, 0.0D, -widthFront, widthFront, light, blockLight);
        vertex(consumer, pose, 0.0D, -widthFront, -widthFront, light, blockLight);

        vertex(consumer, pose, length, -widthBack, widthBack, dark, blockLight);
        vertex(consumer, pose, length, widthBack, widthBack, dark, blockLight);
        vertex(consumer, pose, 0.0D, widthFront, widthFront, light, blockLight);
        vertex(consumer, pose, 0.0D, -widthFront, widthFront, light, blockLight);

        vertex(consumer, pose, length, -widthBack, -widthBack, dark, blockLight);
        vertex(consumer, pose, length, widthBack, -widthBack, dark, blockLight);
        vertex(consumer, pose, 0.0D, widthFront, -widthFront, light, blockLight);
        vertex(consumer, pose, 0.0D, -widthFront, -widthFront, light, blockLight);

        vertex(consumer, pose, length, widthBack, widthBack, dark, blockLight);
        vertex(consumer, pose, length, widthBack, -widthBack, dark, blockLight);
        vertex(consumer, pose, length, -widthBack, -widthBack, dark, blockLight);
        vertex(consumer, pose, length, -widthBack, widthBack, dark, blockLight);

        vertex(consumer, pose, 0.0D, widthFront, widthFront, light, blockLight);
        vertex(consumer, pose, 0.0D, widthFront, -widthFront, light, blockLight);
        vertex(consumer, pose, 0.0D, -widthFront, -widthFront, light, blockLight);
        vertex(consumer, pose, 0.0D, -widthFront, widthFront, light, blockLight);
        poses.popPose();
        super.render(bullet, yaw, partialTick, poses, buffers, packedLight);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               double x, double y, double z, int color, int packedLight) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(color)
                .setLight(packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BulletEntity entity) {
        return TEXTURE;
    }
}
