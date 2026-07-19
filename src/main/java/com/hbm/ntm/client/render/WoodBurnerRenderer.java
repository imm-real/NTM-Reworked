package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.WoodBurnerBlock;
import com.hbm.ntm.blockentity.WoodBurnerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public final class WoodBurnerRenderer implements BlockEntityRenderer<WoodBurnerBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/wood_burner"));

    public WoodBurnerRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(WoodBurnerBlockEntity burner, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(-burner.getBlockState()
                .getValue(WoodBurnerBlock.FACING).toYRot()));
        poses.translate(-0.5D, 0.0D, -0.5D);
        ThermalModelRenderer.render(MODEL, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    @Override public AABB getRenderBoundingBox(WoodBurnerBlockEntity burner) { return burner.renderBounds(); }
    @Override public int getViewDistance() { return 256; }
}
