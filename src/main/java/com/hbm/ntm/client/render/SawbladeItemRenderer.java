package com.hbm.ntm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Reuses the actual Sawmill Blade group, as the old item renderer did. */
public final class SawbladeItemRenderer extends BlockEntityWithoutLevelRenderer {
    public SawbladeItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        if (context == ItemDisplayContext.GUI) {
            // ItemRenderLibrary's sawblade inventory pose, with its unwanted time rotation omitted.
            poses.translate(0.0D, -7.0D, 0.0D);
            poses.scale(6.0F, 6.0F, 6.0F);
            poses.mulPose(Axis.YP.rotationDegrees(-45.0F));
            poses.mulPose(Axis.XP.rotationDegrees(30.0F));
            poses.translate(0.0D, 0.0D, -0.875D);
        } else {
            poses.translate(0.0D, -1.375D, -0.875D);
        }
        ThermalModelRenderer.render(SawmillRenderer.BLADE, poses, buffers, packedLight,
                OverlayTexture.NO_OVERLAY);
        poses.popPose();
    }
}
