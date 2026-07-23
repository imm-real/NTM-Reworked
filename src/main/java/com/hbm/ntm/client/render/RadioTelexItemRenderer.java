package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class RadioTelexItemRenderer extends BlockEntityWithoutLevelRenderer {
    public RadioTelexItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        if (context == ItemDisplayContext.GUI) {
            poses.translate(.5D, .625D, 0D);
            poses.mulPose(Axis.XP.rotationDegrees(-30F));
            poses.mulPose(Axis.YP.rotationDegrees(45F));
            poses.scale(-1F, -1F, -1F);
            poses.translate(0D, -.125D, 0D);
            poses.scale(.375F, .375F, .375F);
        } else {
            boolean ground = context == ItemDisplayContext.GROUND;
            if (!ground) poses.translate(.5D, .25D, 0D);
            float scale = ground ? .375F : .25F;
            poses.scale(scale, scale, scale);
            if (context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                poses.mulPose(Axis.YP.rotationDegrees(90F));
            }
        }
        poses.translate(0D, 0D, -.5D);
        ThermalModelRenderer.render(RadioTelexRenderer.MODEL, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
