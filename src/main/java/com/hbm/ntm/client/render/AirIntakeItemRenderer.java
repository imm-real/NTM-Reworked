package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class AirIntakeItemRenderer extends BlockEntityWithoutLevelRenderer {
    public AirIntakeItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        if (context == ItemDisplayContext.GUI) {
            // The raw OBJ needs five times the encouragement in inventory.
            poses.scale(5.0F, 5.0F, 5.0F);
        }
        AirIntakeRenderer.renderParts(0.0F, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
