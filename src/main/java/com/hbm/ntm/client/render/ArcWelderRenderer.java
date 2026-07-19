package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ArcWelderBlock;
import com.hbm.ntm.blockentity.ArcWelderBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class ArcWelderRenderer implements BlockEntityRenderer<ArcWelderBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/arc_welder"));
    private final ItemRenderer itemRenderer;

    public ArcWelderRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override public void render(ArcWelderBlockEntity welder, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(facingRotation(
                welder.getBlockState().getValue(ArcWelderBlock.FACING))));
        pose.translate(-0.5D, 0D, 0D);
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(MODEL);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.solidBlockSheet());
        renderer.renderModel(pose.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                1F, 1F, 1F, light, overlay);

        ItemStack display = welder.display();
        if (!display.isEmpty()) {
            pose.pushPose();
            pose.translate(0.15625D, 1.125D, 0D);
            pose.mulPose(Axis.YP.rotationDegrees(90F));
            pose.mulPose(Axis.XP.rotationDegrees(-90F));
            pose.scale(1.5F, 1.5F, 1.5F);
            itemRenderer.renderStatic(display.copyWithCount(1), ItemDisplayContext.FIXED,
                    light, overlay, pose, buffers, welder.getLevel(), 0);
            pose.popPose();
        }
        pose.popPose();
    }

    private static float facingRotation(Direction direction) {
        return switch (direction) {
            case NORTH -> 90F;
            case EAST -> 0F;
            case SOUTH -> 270F;
            case WEST -> 180F;
            default -> 0F;
        };
    }

    @Override public AABB getRenderBoundingBox(ArcWelderBlockEntity welder) {
        BlockPos pos = welder.getBlockPos();
        return new AABB(pos.getX() - 1D, pos.getY(), pos.getZ() - 1D,
                pos.getX() + 2D, pos.getY() + 3D, pos.getZ() + 2D);
    }
    @Override public int getViewDistance() { return 256; }
}
