package com.hbm.ntm.client.render;

import com.hbm.ntm.entity.MovingConveyorItemEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Stable belt-top item rendering without the bobbing/rotation of vanilla drops. */
public final class MovingConveyorItemRenderer extends EntityRenderer<MovingConveyorItemEntity> {
    private final ItemRenderer itemRenderer;

    public MovingConveyorItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        shadowRadius = 0.15F;
    }

    @Override
    public void render(MovingConveyorItemEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        if (entity.getItemStack().isEmpty()) return;
        poses.pushPose();
        poses.translate(0.0D, 0.125D, 0.0D);
        poses.mulPose(Axis.YP.rotationDegrees((entity.getId() * 57) % 360));
        poses.scale(0.75F, 0.75F, 0.75F);
        itemRenderer.renderStatic(entity.getItemStack(), ItemDisplayContext.GROUND, packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, poses, buffers,
                entity.level(), entity.getId());
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MovingConveyorItemEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
