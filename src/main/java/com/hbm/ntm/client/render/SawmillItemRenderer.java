package com.hbm.ntm.client.render;

import com.hbm.ntm.item.SawmillMachineBlockItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class SawmillItemRenderer extends BlockEntityWithoutLevelRenderer {
    public SawmillItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        if (context == ItemDisplayContext.GUI) {
            // RenderSawmill#getRenderer#renderInventory. Keep the requested static item blade.
            poses.translate(0.0D, -1.5D, 0.0D);
            poses.scale(3.25F, 3.25F, 3.25F);
        }
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        boolean hasBlade = !SawmillMachineBlockItem.isMissingBlade(stack);
        SawmillRenderer.renderParts(0.0F, hasBlade,
                poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
