package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.OilDerrickBlock;
import com.hbm.ntm.blockentity.OilDerrickBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class OilDerrickRenderer implements BlockEntityRenderer<OilDerrickBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/derrick"));

    public OilDerrickRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(OilDerrickBlockEntity derrick, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(facingRotation(
                derrick.getBlockState().getValue(OilDerrickBlock.FACING))));
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(MODEL);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.solidBlockSheet());
        renderer.renderModel(pose.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                1F, 1F, 1F, light, overlay);
        pose.popPose();
    }

    private static float facingRotation(Direction direction) {
        return switch (direction) {
            case NORTH -> 180F;
            case EAST -> 90F;
            case SOUTH -> 0F;
            case WEST -> 270F;
            default -> 0F;
        };
    }

    @Override
    public AABB getRenderBoundingBox(OilDerrickBlockEntity derrick) {
        BlockPos pos = derrick.getBlockPos();
        return new AABB(pos.getX() - 1D, pos.getY(), pos.getZ() - 1D,
                pos.getX() + 2D, pos.getY() + 10D, pos.getZ() + 2D);
    }

    @Override public int getViewDistance() { return 256; }
}
