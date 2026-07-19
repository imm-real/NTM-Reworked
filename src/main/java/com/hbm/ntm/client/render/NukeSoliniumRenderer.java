package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.NukeSoliniumBlock;
import com.hbm.ntm.blockentity.NukeSoliniumBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Blue Rinse renderer. Same compass allergy as F.L.E.I.J.A. */
public final class NukeSoliniumRenderer implements BlockEntityRenderer<NukeSoliniumBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/solinium_body"));

    public NukeSoliniumRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NukeSoliniumBlockEntity bomb, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        float rotation = 180.0F - bomb.getBlockState().getValue(NukeSoliniumBlock.FACING).toYRot();
        poses.mulPose(Axis.YP.rotationDegrees(rotation));
        ThermalModelRenderer.render(MODEL, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(NukeSoliniumBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX() - 3, pos.getY() - 1, pos.getZ() - 3,
                pos.getX() + 4, pos.getY() + 3, pos.getZ() + 4);
    }

    @Override public int getViewDistance() { return 256; }
}
