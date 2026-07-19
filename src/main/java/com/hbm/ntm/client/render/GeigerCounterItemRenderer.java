package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Geiger counter model for slots, hands and other hostile camera angles. */
public final class GeigerCounterItemRenderer extends BlockEntityWithoutLevelRenderer {
    public GeigerCounterItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        if (context == ItemDisplayContext.GUI) {
            // Inventory uses the OBJ. Undo slot centering/Y reflection before its old camera pose.
            poses.translate(0.5D, 0.5D, 0.5D);
            poses.scale(1.0F, -1.0F, 1.0F);
            poses.translate(0.0D, 2.0D / 16.0D, 0.0D);
            poses.mulPose(Axis.XN.rotationDegrees(30.0F));
            poses.mulPose(Axis.YP.rotationDegrees(45.0F));
            poses.scale(-10.0F / 16.0F, -10.0F / 16.0F, -10.0F / 16.0F);
            poses.translate(0.2D, 0.0D, 0.0D);
            poses.mulPose(Axis.YP.rotationDegrees(90.0F));
            GeigerCounterRenderer.renderModel(poses, buffers, packedLight, packedOverlay);
            poses.popPose();
            return;
        }

        if (context == ItemDisplayContext.GROUND) poses.scale(1.5F, 1.5F, 1.5F);
        else poses.translate(0.5D, 0.25D, 0.0D);
        poses.scale(0.25F, 0.25F, 0.25F);
        if (context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        }
        poses.translate(0.2D, 0.0D, 0.0D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        GeigerCounterRenderer.renderModel(poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
