package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ThermalMultiblockBlock;
import com.hbm.ntm.blockentity.StirlingBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public final class StirlingRenderer implements BlockEntityRenderer<StirlingBlockEntity> {
    public static final ModelResourceLocation BASE = model("stirling_base");
    public static final ModelResourceLocation COG = model("stirling_cog");
    public static final ModelResourceLocation COG_SMALL = model("stirling_cog_small");
    public static final ModelResourceLocation PISTON = model("stirling_piston");

    public StirlingRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StirlingBlockEntity stirling, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float rotation = Mth.lerp(partialTick, stirling.lastSpin(), stirling.spin());
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(-stirling.getBlockState()
                .getValue(ThermalMultiblockBlock.FACING).toYRot()));

        renderParts(rotation, stirling.hasCog(), poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    public static void renderParts(float rotation, boolean hasCog, PoseStack poses,
                                   MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ThermalModelRenderer.render(BASE, poses, buffers, packedLight, packedOverlay);
        if (hasCog) {
            poses.pushPose();
            poses.translate(0.0D, 1.375D, 0.0D);
            poses.mulPose(Axis.ZP.rotationDegrees(-rotation));
            poses.translate(0.0D, -1.375D, 0.0D);
            ThermalModelRenderer.render(COG, poses, buffers, packedLight, packedOverlay);
            poses.popPose();
        }

        poses.pushPose();
        poses.translate(0.0D, 1.375D, 0.25D);
        poses.mulPose(Axis.XP.rotationDegrees(rotation * 2.0F + 3.0F));
        poses.translate(0.0D, -1.375D, -0.25D);
        ThermalModelRenderer.render(COG_SMALL, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(Math.sin(rotation * Math.PI / 90.0D) * 0.25D + 0.125D, 0.0D, 0.0D);
        ThermalModelRenderer.render(PISTON, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(StirlingBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
    }

    @Override public int getViewDistance() { return 256; }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/" + path));
    }
}
