package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.FluidBurnerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public final class FluidBurnerRenderer implements BlockEntityRenderer<FluidBurnerBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/fluid_burner"));

    public FluidBurnerRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(FluidBurnerBlockEntity burner, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        ThermalModelRenderer.render(MODEL, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    @Override public AABB getRenderBoundingBox(FluidBurnerBlockEntity burner) {
        BlockPos pos = burner.getBlockPos();
        return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
    }

    @Override public int getViewDistance() { return 256; }
}
