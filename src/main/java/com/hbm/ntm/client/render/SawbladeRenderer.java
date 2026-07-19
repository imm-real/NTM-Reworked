package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.SawbladeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class SawbladeRenderer extends EntityRenderer<SawbladeEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/block/sawmill.png");

    public SawbladeRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.5F;
    }

    @Override
    public void render(SawbladeEntity blade, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(-blade.orientation().toYRot()));
        poses.translate(0.0D, 0.0D, -1.0D);
        if (!blade.embedded()) {
            poses.mulPose(Axis.ZN.rotationDegrees((System.currentTimeMillis() % 1_800L) / 3.0F));
        }
        poses.translate(0.0D, -1.375D, 0.0D);
        ThermalModelRenderer.render(SawmillRenderer.BLADE, poses, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        poses.popPose();
        super.render(blade, yaw, partialTick, poses, buffers, packedLight);
    }

    @Override public ResourceLocation getTextureLocation(SawbladeEntity entity) { return TEXTURE; }
}
