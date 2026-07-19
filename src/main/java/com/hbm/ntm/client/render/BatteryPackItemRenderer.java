package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class BatteryPackItemRenderer extends BlockEntityWithoutLevelRenderer {
    public BatteryPackItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        if (context == ItemDisplayContext.GUI) {
            poses.translate(0.5D, 0.5D, 0.5D);
            // ItemRenderBatteryPack#renderInventory, after the shared camera in the item model.
            poses.translate(0.0D, -3.0D, 0.0D);
            poses.scale(5.0F, 5.0F, 5.0F);
        } else if (context == ItemDisplayContext.GROUND) {
            poses.translate(0.5D, 0.15D, 0.5D);
            // ItemRenderBase used entity scale 1.5 followed by its common 0.25 scale.
            poses.scale(0.375F, 0.375F, 0.375F);
        } else {
            poses.translate(0.5D, 0.2D, 0.5D);
            poses.mulPose(Axis.YP.rotationDegrees(180.0F));
            // Every non-GUI context shares this scale. Democracy at last.
            poses.scale(0.25F, 0.25F, 0.25F);
        }
        BatterySocketRenderer.renderBattery(stack, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
