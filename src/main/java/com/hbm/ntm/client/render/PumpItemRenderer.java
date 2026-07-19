package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class PumpItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final boolean electric;

    public PumpItemRenderer(boolean electric) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.electric = electric;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        if (context == ItemDisplayContext.GUI) {
            // RenderPump#getRenderer#renderInventory.
            poses.translate(0.0D, -3.0D, 0.0D);
            poses.scale(2.5F, 2.5F, 2.5F);
        }
        float rotation = (System.currentTimeMillis() % 3_600L) * 0.1F;
        PumpRenderer.renderParts(electric, rotation, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
