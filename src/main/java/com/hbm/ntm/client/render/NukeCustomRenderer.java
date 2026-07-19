package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.NukeCustomBlock;
import com.hbm.ntm.blockentity.NukeCustomBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Little Boy mesh wearing the custom-nuke paint job. */
public final class NukeCustomRenderer implements BlockEntityRenderer<NukeCustomBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/nuke_custom_body"));

    public NukeCustomRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NukeCustomBlockEntity bomb, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        float facing = bomb.getBlockState().getValue(NukeCustomBlock.FACING).toYRot();
        // Rotate opposite facing, then move two blocks because the OBJ origin fled.
        poses.mulPose(Axis.YP.rotationDegrees(-facing));
        poses.translate(-2.0D, 0.0D, 0.0D);
        ThermalModelRenderer.render(MODEL, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(NukeCustomBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX() - 3, pos.getY() - 1, pos.getZ() - 3,
                pos.getX() + 4, pos.getY() + 3, pos.getZ() + 4);
    }

    @Override public int getViewDistance() { return 256; }
}
