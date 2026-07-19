package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.CrucibleBlock;
import com.hbm.ntm.blockentity.CrucibleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class CrucibleRenderer implements BlockEntityRenderer<CrucibleBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/crucible"));
    private static final ResourceLocation LAVA = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/models/machines/lava.png");

    public CrucibleRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(CrucibleBlockEntity crucible, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(.5D, 0D, .5D);
        pose.mulPose(Axis.YP.rotationDegrees(rotation(crucible.getBlockState().getValue(CrucibleBlock.FACING))));
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(MODEL);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        renderer.renderModel(pose.last(), buffers.getBuffer(Sheets.solidBlockSheet()), Blocks.STONE.defaultBlockState(),
                model, 1F, 1F, 1F, light, overlay);

        int mass = crucible.recipeTotal() + crucible.wasteTotal();
        if (mass > 0) {
            float y = (float) (.5D + (double) mass / (CrucibleBlockEntity.RECIPE_CAPACITY
                    + CrucibleBlockEntity.WASTE_CAPACITY) * .875D);
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(LAVA));
            PoseStack.Pose current = pose.last();
            vertex(consumer, current, -1F, y, -1F, 0, 0);
            vertex(consumer, current, -1F, y, 1F, 0, 1);
            vertex(consumer, current, 1F, y, 1F, 1, 1);
            vertex(consumer, current, 1F, y, -1F, 1, 0);
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(1F, 1F, 1F, 1F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0F, 1F, 0F);
    }

    private static float rotation(Direction direction) {
        return switch (direction) { case NORTH -> 90F; case EAST -> 180F; case SOUTH -> 270F; default -> 0F; };
    }

    @Override public AABB getRenderBoundingBox(CrucibleBlockEntity crucible) {
        BlockPos pos = crucible.getBlockPos();
        return new AABB(pos.getX() - 1D, pos.getY(), pos.getZ() - 1D,
                pos.getX() + 2D, pos.getY() + 2D, pos.getZ() + 2D);
    }
    @Override public int getViewDistance() { return 256; }
}
