package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.CogEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class CogRenderer extends EntityRenderer<CogEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/models/machines/stirling.png");

    public CogRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.5F;
    }

    @Override
    public void render(CogEntity cog, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(-cog.orientation().toYRot()));
        poses.translate(0.0D, 0.0D, -1.0D);
        if (!cog.embedded()) {
            poses.mulPose(Axis.ZN.rotationDegrees((System.currentTimeMillis() % 1080L) / 3.0F));
        }
        poses.translate(0.0D, -1.375D, 0.0D);
        ThermalModelRenderer.render(StirlingRenderer.COG, poses, buffers, packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        poses.popPose();
        super.render(cog, yaw, partialTick, poses, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CogEntity entity) {
        return TEXTURE;
    }
}
