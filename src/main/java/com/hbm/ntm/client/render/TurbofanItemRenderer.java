package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Pocket turbofan with stopped blades and a reassuringly cold tail. */
public final class TurbofanItemRenderer extends BlockEntityWithoutLevelRenderer {
    public TurbofanItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        if (context == ItemDisplayContext.GUI) {
            // RenderTurbofan's local inventory transform, after ItemRenderBase's shared camera.
            poses.mulPose(Axis.YP.rotationDegrees(90.0F));
            poses.scale(2.25F, 2.25F, 2.25F);
        }
        TurbofanRenderer.renderParts(0.0F, false, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
