package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.ShrapnelModel;
import com.hbm.ntm.entity.ShrapnelEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/** Flips shrapnel over and spins it ten degrees per tick. Aerodynamics. */
public final class ShrapnelRenderer extends EntityRenderer<ShrapnelEntity> {
    private static final float INV_SQRT_THREE = (float) (1.0D / Math.sqrt(3.0D));
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/entity/shrapnel.png");

    private final ShrapnelModel model;

    public ShrapnelRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new ShrapnelModel(context.bakeLayer(ShrapnelModel.LAYER));
        shadowRadius = 0.0F;
    }

    @Override
    public void render(ShrapnelEntity shrapnel, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.mulPose(Axis.XP.rotationDegrees(180.0F));
        float spin = (shrapnel.tickCount % 360) * 10.0F + partialTick;
        poses.mulPose(new Quaternionf().rotationAxis(
                spin * ((float) Math.PI / 180.0F), INV_SQRT_THREE, INV_SQRT_THREE, INV_SQRT_THREE));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        model.render(poses, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poses.popPose();
        super.render(shrapnel, yaw, partialTick, poses, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ShrapnelEntity entity) {
        return TEXTURE;
    }
}
