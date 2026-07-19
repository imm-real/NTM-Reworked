package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ThermalMultiblockBlock;
import com.hbm.ntm.blockentity.FireboxBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public final class HeatingOvenRenderer implements BlockEntityRenderer<FireboxBlockEntity> {
    public static final ModelResourceLocation MAIN = model("heating_oven_main");
    public static final ModelResourceLocation DOOR = model("heating_oven_door");
    public static final ModelResourceLocation EMPTY = model("heating_oven_empty");
    public static final ModelResourceLocation BURNING = model("heating_oven_burning");

    public HeatingOvenRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FireboxBlockEntity oven, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(-oven.getBlockState()
                .getValue(ThermalMultiblockBlock.FACING).toYRot() - 90.0F));
        ThermalModelRenderer.render(MAIN, poses, buffers, packedLight, packedOverlay);

        float door = Mth.lerp(partialTick, oven.previousDoorAngle(), oven.doorAngle());
        poses.pushPose();
        poses.translate(0.0D, 0.0D, door * 0.75D / 135.0D);
        ThermalModelRenderer.render(DOOR, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        ThermalModelRenderer.render(oven.wasOn() ? BURNING : EMPTY, poses, buffers,
                oven.wasOn() ? LightTexture.FULL_BRIGHT : packedLight, packedOverlay);
        poses.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(FireboxBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 1, pos.getZ() + 2);
    }

    @Override public int getViewDistance() { return 256; }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/" + path));
    }
}
