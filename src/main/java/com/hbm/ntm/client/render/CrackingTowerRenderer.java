package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.CrackingTowerBlock;
import com.hbm.ntm.blockentity.CrackingTowerBlockEntity;
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

/** Static catalytic cracking tower, dynamic OSHA violation. */
public final class CrackingTowerRenderer implements BlockEntityRenderer<CrackingTowerBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/catalytic_cracker"));

    public CrackingTowerRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(CrackingTowerBlockEntity tower, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(facingRotation(
                tower.getBlockState().getValue(CrackingTowerBlock.FACING))));
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
            case NORTH -> 90F;
            case WEST -> 180F;
            case SOUTH -> 270F;
            case EAST -> 0F;
            default -> 0F;
        };
    }

    @Override public AABB getRenderBoundingBox(CrackingTowerBlockEntity tower) {
        BlockPos pos = tower.getBlockPos();
        return new AABB(pos.getX() - 3D, pos.getY(), pos.getZ() - 3D,
                pos.getX() + 4D, pos.getY() + 16D, pos.getZ() + 4D);
    }

    @Override public int getViewDistance() { return 256; }
}
