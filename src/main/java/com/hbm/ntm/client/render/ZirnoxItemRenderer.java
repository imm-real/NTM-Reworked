package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ZirnoxItemRenderer extends BlockEntityWithoutLevelRenderer {
    public ZirnoxItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                       MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        if (context == ItemDisplayContext.GUI) {
            // Reactor inventory hook. Compensate for GUI's Y flip and 16x scale first.
            poses.translate(0.5D, 0.375D, 0.5D);
            poses.mulPose(Axis.XP.rotationDegrees(30.0F));
            poses.mulPose(Axis.YP.rotationDegrees(45.0F));
            poses.scale(-1.0F, 1.0F, -1.0F);
            poses.translate(0.0D, -0.125D, 0.0D);
            float inventoryScale = 2.8F * 0.75F / 16.0F;
            poses.scale(inventoryScale, inventoryScale, inventoryScale);
        } else {
            // Held items get the half/quarter offset; dropped items get 1.5x scale;
            // first person and entities get the ninety-degree yaw.
            boolean ground = context == ItemDisplayContext.GROUND;
            if (!ground) poses.translate(0.5D, 0.25D, 0.0D);
            float scale = 0.25F * 0.75F * (ground ? 1.5F : 1.0F);
            poses.scale(scale, scale, scale);
            if (context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
        }
        ThermalModelRenderer.render(ZirnoxRenderer.MODEL, poses, buffers, light, overlay);
        poses.popPose();
    }
}
