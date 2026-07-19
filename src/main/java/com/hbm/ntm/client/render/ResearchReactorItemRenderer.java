package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Research reactor squeezed through the old inventory camera. */
public final class ResearchReactorItemRenderer extends BlockEntityWithoutLevelRenderer {
    public ResearchReactorItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        if (context == ItemDisplayContext.GUI) {
            poses.translate(0.5D, 0.625D, 0.0D);
            poses.mulPose(Axis.XP.rotationDegrees(-30.0F));
            poses.mulPose(Axis.YP.rotationDegrees(45.0F));
            poses.scale(-1.0F, -1.0F, -1.0F);
            poses.translate(0.0D, -0.25D, 0.0D);
            poses.scale(0.25F, 0.25F, 0.25F);
        } else {
            boolean ground = context == ItemDisplayContext.GROUND;
            if (!ground) poses.translate(0.5D, 0.25D, 0.0D);
            float scale = 0.25F * (ground ? 1.5F : 1.0F);
            poses.scale(scale, scale, scale);
            if (context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
        }
        ResearchReactorRenderer.renderStatic(poses, buffers, light, overlay);
        poses.popPose();
    }
}
