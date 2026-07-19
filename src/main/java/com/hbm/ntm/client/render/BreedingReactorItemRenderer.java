package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class BreedingReactorItemRenderer extends BlockEntityWithoutLevelRenderer {
    public BreedingReactorItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                       MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        if (context == ItemDisplayContext.GUI) {
            poses.translate(.5D, .625D, 0D);
            poses.mulPose(Axis.XP.rotationDegrees(-30F));
            poses.mulPose(Axis.YP.rotationDegrees(45F));
            poses.scale(-1F, -1F, -1F);
            poses.translate(0D, -4.5D / 16D, 0D);
            float scale = 4.5F / 16F;
            poses.scale(scale, scale, scale);
        } else {
            boolean ground = context == ItemDisplayContext.GROUND;
            if (!ground) poses.translate(.5D, .25D, 0D);
            float scale = .25F * (ground ? 1.5F : 1F);
            poses.scale(scale, scale, scale);
            if (context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) poses.mulPose(Axis.YP.rotationDegrees(90F));
        }
        ThermalModelRenderer.render(BreedingReactorRenderer.MODEL, poses, buffers, light, overlay);
        poses.popPose();
    }
}
