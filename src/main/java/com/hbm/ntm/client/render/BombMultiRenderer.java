package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.BombMultiBlock;
import com.hbm.ntm.blockentity.BombMultiBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Center, flip, rotate. Bomb now faces the correct victim. */
public final class BombMultiRenderer implements BlockEntityRenderer<BombMultiBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/bomb_multi_body"));

    public BombMultiRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BombMultiBlockEntity bomb, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        poses.mulPose(Axis.XP.rotationDegrees(180.0F));
        float yaw = 90.0F + bomb.getBlockState().getValue(BombMultiBlock.FACING).toYRot();
        poses.mulPose(Axis.YP.rotationDegrees(yaw));
        ThermalModelRenderer.render(MODEL, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(BombMultiBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos).inflate(1.0D);
    }

    @Override public int getViewDistance() { return 256; }
}
