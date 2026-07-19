package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.MicrowaveBlock;
import com.hbm.ntm.blockentity.MicrowaveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Microwave body plus a turntable powered directly by wall-clock time. */
public final class MicrowaveRenderer implements BlockEntityRenderer<MicrowaveBlockEntity> {
    public static final ModelResourceLocation BODY = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/microwave_body"));
    public static final ModelResourceLocation PLATE = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/microwave_plate"));

    public MicrowaveRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(MicrowaveBlockEntity microwave, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, -0.785D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(facingRotation(
                microwave.getBlockState().getValue(MicrowaveBlock.FACING))));
        poses.translate(-0.5D, 0.0D, 0.65D);
        ThermalModelRenderer.render(BODY, poses, buffers, packedLight, packedOverlay);

        if (microwave.time() > 0) {
            double rotation = Util.getMillis() * microwave.speed() / 10.0D % 360.0D;
            poses.translate(0.575D, 0.0D, -0.45D);
            poses.mulPose(Axis.YP.rotationDegrees((float) rotation));
            poses.translate(-0.575D, 0.0D, 0.45D);
        }
        ThermalModelRenderer.render(PLATE, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    private static float facingRotation(Direction facing) {
        return switch (facing) {
            case NORTH -> 0.0F;
            case WEST -> 90.0F;
            case SOUTH -> 180.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
    }

    @Override public AABB getRenderBoundingBox(MicrowaveBlockEntity microwave) {
        return microwave.renderBounds();
    }
    @Override public int getViewDistance() { return 256; }
}
