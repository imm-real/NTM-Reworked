package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.MachinePressBlockEntity;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class MachinePressRenderer implements BlockEntityRenderer<MachinePressBlockEntity> {
    public static final ModelResourceLocation BODY_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/press_body")
    );
    public static final ModelResourceLocation HEAD_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/press_head")
    );

    private final ItemRenderer itemRenderer;

    public MachinePressRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            MachinePressBlockEntity press,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        renderModel(BODY_MODEL, poseStack, buffers, packedLight, packedOverlay, true, 0.0D);

        double normalized = (press.lastPress() + (press.renderPress() - press.lastPress()) * partialTick)
                / MachinePressBlockEntity.MAX_PRESS;
        double headOffset = Mth.clamp(1.0D - normalized, 0.0D, 1.0D) * 0.875D;
        renderModel(HEAD_MODEL, poseStack, buffers, packedLight, packedOverlay, false, headOffset);

        ItemStack input = press.renderStack();
        if (!input.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5D, 1.0D, -0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.translate(0.0D, 0.896875D, 0.0D);
            ItemStack displayed = input.copyWithCount(1);
            itemRenderer.renderStatic(displayed, ItemDisplayContext.FIXED, packedLight, packedOverlay,
                    poseStack, buffers, press.getLevel(), 0);
            poseStack.popPose();
        }
    }

    private void renderModel(
            ModelResourceLocation location,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            boolean rotateBody,
            double yOffset
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(location);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.solidBlockSheet());

        poseStack.pushPose();
        poseStack.translate(0.5D, yOffset, 0.5D);
        if (rotateBody) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        } else {
            poseStack.scale(0.99F, 1.0F, 0.99F);
        }
        renderer.renderModel(poseStack.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(MachinePressBlockEntity press) {
        BlockPos position = press.getBlockPos();
        return new AABB(position.getX(), position.getY(), position.getZ(),
                position.getX() + 1.0D, position.getY() + 3.0D, position.getZ() + 1.0D);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
