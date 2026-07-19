package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.BlastFurnaceBlock;
import com.hbm.ntm.blockentity.BlastFurnaceBlockEntity;
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

/** Original complete Blast Furnace OBJ rendered once from its bottom-center core. */
public final class BlastFurnaceRenderer implements BlockEntityRenderer<BlastFurnaceBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/blast_furnace_body"));

    public BlastFurnaceRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(BlastFurnaceBlockEntity furnace, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(rotation(
                furnace.getBlockState().getValue(BlastFurnaceBlock.FACING))));
        ThermalModelRenderer.render(MODEL, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    private static float rotation(Direction facing) {
        return switch (facing) {
            case EAST -> 0F;
            case NORTH -> 90F;
            case WEST -> 180F;
            case SOUTH -> 270F;
            default -> 0F;
        };
    }

    @Override public AABB getRenderBoundingBox(BlastFurnaceBlockEntity furnace) {
        BlockPos pos = furnace.getBlockPos();
        return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 7, pos.getZ() + 2);
    }

    @Override public int getViewDistance() { return 256; }
}
