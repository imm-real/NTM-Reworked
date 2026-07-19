package com.hbm.ntm.client.render;

import com.hbm.ntm.entity.PrimedExplosiveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class PrimedExplosiveRenderer extends EntityRenderer<PrimedExplosiveEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public PrimedExplosiveRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.5F;
        blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(PrimedExplosiveEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.translate(0.0F, 0.5F, 0.0F);
        int fuse = entity.getFuse();
        if ((float) fuse - partialTick + 1.0F < 10.0F) {
            float progress = 1.0F - ((float) fuse - partialTick + 1.0F) / 10.0F;
            progress = Mth.clamp(progress, 0.0F, 1.0F);
            progress *= progress;
            progress *= progress;
            float scale = 1.0F + progress * 0.3F;
            poses.scale(scale, scale, scale);
        }

        poses.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poses.translate(-0.5F, -0.5F, 0.5F);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        int overlay = OverlayTexture.NO_OVERLAY;
        if (fuse / 5 % 2 == 0) {
            float flash = (1.0F - ((float) fuse - partialTick + 1.0F) / 100.0F) * 0.8F;
            overlay = OverlayTexture.pack(OverlayTexture.u(Mth.clamp(flash, 0.0F, 1.0F)), 10);
        }
        blockRenderer.renderSingleBlock(entity.getBlockState(), poses, buffers, packedLight, overlay);
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PrimedExplosiveEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
