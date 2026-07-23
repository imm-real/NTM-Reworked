package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.RadioTelexBlock;
import com.hbm.ntm.blockentity.RadioTelexBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public final class RadioTelexRenderer implements BlockEntityRenderer<RadioTelexBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/radio_telex"));

    public RadioTelexRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RadioTelexBlockEntity telex, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(.5D, 0D, .5D);
        poses.mulPose(Axis.YP.rotationDegrees(rotation(
                telex.getBlockState().getValue(RadioTelexBlock.FACING))));
        ThermalModelRenderer.render(MODEL, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    private static float rotation(Direction facing) {
        return switch (facing) {
            case NORTH -> 90F;
            case WEST -> 180F;
            case SOUTH -> 270F;
            case EAST -> 0F;
            default -> 0F;
        };
    }

    @Override
    public AABB getRenderBoundingBox(RadioTelexBlockEntity telex) {
        BlockPos position = telex.getBlockPos();
        return new AABB(position.getX() - 1D, position.getY(), position.getZ() - 1D,
                position.getX() + 2D, position.getY() + 2D, position.getZ() + 2D);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
