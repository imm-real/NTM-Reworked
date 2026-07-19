package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Static pocket FEnSU. Only the one bolted down gets to dance. */
public final class FensuItemRenderer extends BlockEntityWithoutLevelRenderer {
    public FensuItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        if (context == ItemDisplayContext.GUI) {
            poses.translate(0.5D, 0.5D, 0.5D);
            // The battery renderer's deeply normal inventory offset.
            poses.translate(0.0D, -3.0D, 0.0D);
            poses.scale(2.5F, 2.5F, 2.5F);
            poses.mulPose(Axis.YP.rotationDegrees(-90.0F));
            poses.scale(0.5F, 0.5F, 0.5F);
        } else {
            poses.translate(0.5D, 0.5D, 0.5D);
            poses.mulPose(Axis.YP.rotationDegrees(-90F));
            poses.scale(0.08F, 0.08F, 0.08F);
            poses.translate(0D, -5.5D, 0D);
        }
        FensuRenderer.renderStatic(poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
