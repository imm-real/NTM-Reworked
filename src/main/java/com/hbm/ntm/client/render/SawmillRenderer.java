package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ThermalMultiblockBlock;
import com.hbm.ntm.blockentity.SawmillBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public final class SawmillRenderer implements BlockEntityRenderer<SawmillBlockEntity> {
    public static final ModelResourceLocation MAIN = model("sawmill_main");
    public static final ModelResourceLocation BLADE = model("sawmill_blade");
    public static final ModelResourceLocation GEAR_LEFT = model("sawmill_gear_left");
    public static final ModelResourceLocation GEAR_RIGHT = model("sawmill_gear_right");

    public SawmillRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SawmillBlockEntity sawmill, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float rotation = Mth.lerp(partialTick, sawmill.lastSpin(), sawmill.spin());
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(-sawmill.getBlockState()
                .getValue(ThermalMultiblockBlock.FACING).toYRot()));
        renderParts(rotation, sawmill.hasBlade(), poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    public static void renderParts(float rotation, boolean hasBlade, PoseStack poses,
                                   MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ThermalModelRenderer.render(MAIN, poses, buffers, packedLight, packedOverlay);
        if (hasBlade) {
            poses.pushPose();
            poses.translate(0.0D, 1.375D, 0.0D);
            poses.mulPose(Axis.ZP.rotationDegrees(-rotation * 2.0F));
            poses.translate(0.0D, -1.375D, 0.0D);
            ThermalModelRenderer.render(BLADE, poses, buffers, packedLight, packedOverlay);
            poses.popPose();
        }

        poses.pushPose();
        poses.translate(0.5625D, 1.375D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees(rotation));
        poses.translate(-0.5625D, -1.375D, 0.0D);
        ThermalModelRenderer.render(GEAR_LEFT, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(-0.5625D, 1.375D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees(-rotation));
        poses.translate(0.5625D, -1.375D, 0.0D);
        ThermalModelRenderer.render(GEAR_RIGHT, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    @Override public AABB getRenderBoundingBox(SawmillBlockEntity blockEntity) { return blockEntity.renderBounds(); }
    @Override public int getViewDistance() { return 256; }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "block/" + path));
    }
}
