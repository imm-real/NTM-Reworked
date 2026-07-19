package com.hbm.ntm.client.render;

import com.hbm.ntm.block.ChargeBlock;
import com.hbm.ntm.blockentity.ChargeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public final class ChargeRenderer implements BlockEntityRenderer<ChargeBlockEntity> {
    private final Font font;

    public ChargeRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(ChargeBlockEntity charge, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (charge.getBlockState().getValue(ChargeBlock.FACING)) {
            case DOWN -> poses.mulPose(Axis.ZP.rotationDegrees(180.0F));
            case UP -> { }
            case NORTH -> {
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
                poses.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            }
            case SOUTH -> {
                poses.mulPose(Axis.YP.rotationDegrees(-90.0F));
                poses.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            }
            case WEST -> {
                poses.mulPose(Axis.YP.rotationDegrees(180.0F));
                poses.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            }
            case EAST -> poses.mulPose(Axis.ZP.rotationDegrees(-90.0F));
        }

        String text = charge.minutes() + ":" + charge.seconds();
        poses.translate(-0.05F, -0.185F, 0.15F);
        poses.scale(0.0125F, -0.0125F, 0.0125F);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(90.0F));
        font.drawInBatch(text, 0.0F, 0.0F, 0x00FF00, false, poses.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        poses.popPose();
    }
}
