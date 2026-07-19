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

public final class FireboxRenderer implements BlockEntityRenderer<FireboxBlockEntity> {
    public static final ModelResourceLocation MAIN = model("firebox_main");
    public static final ModelResourceLocation DOOR = model("firebox_door");
    public static final ModelResourceLocation EMPTY = model("firebox_empty");
    public static final ModelResourceLocation BURNING = model("firebox_burning");

    public FireboxRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FireboxBlockEntity firebox, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(-firebox.getBlockState()
                .getValue(ThermalMultiblockBlock.FACING).toYRot() - 90.0F));
        ThermalModelRenderer.render(MAIN, poses, buffers, packedLight, packedOverlay);

        float door = Mth.lerp(partialTick, firebox.previousDoorAngle(), firebox.doorAngle());
        poses.pushPose();
        poses.translate(1.375D, 0.0D, 0.375D);
        poses.mulPose(Axis.YP.rotationDegrees(-door));
        poses.translate(-1.375D, 0.0D, -0.375D);
        ThermalModelRenderer.render(DOOR, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        ThermalModelRenderer.render(firebox.wasOn() ? BURNING : EMPTY, poses, buffers,
                firebox.wasOn() ? LightTexture.FULL_BRIGHT : packedLight, packedOverlay);
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
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/" + path));
    }
}
