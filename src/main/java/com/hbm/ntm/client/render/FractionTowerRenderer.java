package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.FractionTowerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** One static fractioning tower floor. Stack irresponsibly. */
public final class FractionTowerRenderer implements BlockEntityRenderer<FractionTowerBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/fraction_tower"));

    public FractionTowerRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(FractionTowerBlockEntity tower, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(MODEL);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.solidBlockSheet());
        renderer.renderModel(pose.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                1F, 1F, 1F, light, overlay);
        pose.popPose();
    }

    @Override public AABB getRenderBoundingBox(FractionTowerBlockEntity tower) {
        BlockPos pos = tower.getBlockPos();
        return new AABB(pos.getX() - 1D, pos.getY(), pos.getZ() - 1D,
                pos.getX() + 2D, pos.getY() + 3D, pos.getZ() + 2D);
    }

    @Override public int getViewDistance() { return 256; }
}
