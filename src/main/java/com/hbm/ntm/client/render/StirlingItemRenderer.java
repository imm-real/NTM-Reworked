package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Pocket Stirling engine whose cog and piston never get a lunch break. */
public final class StirlingItemRenderer extends BlockEntityWithoutLevelRenderer {
    public StirlingItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        // Cancel ItemRenderer's builtin/entity half-block offset. The JSON display
        // transforms are still applied before this renderer is called.
        poses.translate(0.5D, 0.5D, 0.5D);
        if (context == ItemDisplayContext.GUI) {
            // RenderStirling#getRenderer: inventory pose followed by its common 90-degree turn.
            poses.translate(0.0D, -1.5D, 0.0D);
            poses.scale(3.25F, 3.25F, 3.25F);
            poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        }
        float rotation = (System.currentTimeMillis() % 3_600L) * 0.1F;
        StirlingRenderer.renderParts(rotation, true, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
