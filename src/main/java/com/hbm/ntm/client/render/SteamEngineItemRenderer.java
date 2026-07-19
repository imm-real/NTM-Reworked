package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class SteamEngineItemRenderer extends BlockEntityWithoutLevelRenderer {
    public SteamEngineItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        if (context == ItemDisplayContext.GUI) {
            // RenderSteamEngine#getRenderer#renderInventory.
            poses.mulPose(Axis.YP.rotationDegrees(-90.0F));
            poses.translate(0.0D, -1.5D, 0.0D);
            poses.scale(2.0F, 2.0F, 2.0F);
        }
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        float rotation = (System.currentTimeMillis() % 3_600L) * 0.1F;
        SteamEngineRenderer.renderParts(rotation, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
