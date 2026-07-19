package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class IndustrialTurbineItemRenderer extends BlockEntityWithoutLevelRenderer {
    public IndustrialTurbineItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        if (context == ItemDisplayContext.GUI) {
            // RenderIndustrialTurbine#getRenderer: inventory pose, then common model pose.
            poses.translate(1.0D, 0.0D, 0.0D);
            poses.scale(3.0F, 3.0F, 3.0F);
            poses.mulPose(Axis.YP.rotationDegrees(90.0F));
            poses.scale(0.75F, 0.75F, 0.75F);
            poses.translate(0.5D, 0.0D, 0.0D);
        } else {
            poses.mulPose(Axis.YP.rotationDegrees(90.0F));
            poses.translate(0.5D, 0.0D, 0.0D);
        }
        float rotation = (float) ((System.currentTimeMillis() / 5L) % 336L);
        IndustrialTurbineRenderer.renderParts(0, rotation, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
