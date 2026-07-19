package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class BatterySocketItemRenderer extends BlockEntityWithoutLevelRenderer {
    public BatterySocketItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        if (context == ItemDisplayContext.GUI) {
            poses.translate(0.5D, 0.5D, 0.5D);
            // RenderBatterySocket#getRenderer: the item did not inherit the world's model offset.
            poses.translate(0.0D, -2.0D, 0.0D);
            poses.scale(5.0F, 5.0F, 5.0F);
        } else {
            poses.translate(0.5D, 0.0D, 0.5D);
            poses.scale(0.35F, 0.35F, 0.35F);
            poses.translate(-0.5D, 0.0D, 0.5D);
        }
        BatterySocketRenderer.renderSocket(poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
