package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.B93Model;
import com.hbm.ntm.item.B93Item;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Original ItemRenderGunAnim transforms with the complete historical B93 model. */
public final class B93ItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation ICON = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "item/gun_b93_icon"));
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/models/model_b93.png");
    private final B93Model model;

    public B93ItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        model = new B93Model(Minecraft.getInstance().getEntityModels().bakeLayer(B93Model.LAYER));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        if (context == ItemDisplayContext.GUI) {
            renderIcon(poses, buffers, packedLight, packedOverlay);
            poses.popPose();
            return;
        }
        poses.translate(0.5D, 0.5D, 0.5D);
        if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            poses.mulPose(Axis.ZN.rotationDegrees(135.0F));
            poses.translate(-0.5D, 0.0D, -0.2D);
            poses.scale(0.25F, 0.25F, 0.25F);
            poses.translate(-0.2D, -0.1D, -0.1D);
            float rotation = B93Item.rotationFromAnimation(stack);
            if (rotation > 0.0F) {
                poses.mulPose(Axis.ZN.rotationDegrees(rotation * 90.0F));
                poses.translate(-rotation, -rotation, 0.0D);
            }
        } else {
            if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                applyLegacyEquippedBridge(context, poses);
            }
            poses.mulPose(Axis.ZN.rotationDegrees(200.0F));
            poses.mulPose(Axis.YP.rotationDegrees(75.0F));
            poses.mulPose(Axis.XN.rotationDegrees(30.0F));
            poses.translate(0.0D, -0.2D, -0.5D);
            poses.mulPose(Axis.ZN.rotationDegrees(5.0F));
            poses.scale(0.5F, 0.5F, 0.5F);
            poses.translate(-0.3D, -0.4D, 0.15D);
        }

        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        model.renderSolid(poses, solid, packedLight, OverlayTexture.NO_OVERLAY,
                B93Item.translationFromAnimation(stack));
        VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        model.renderGlass(poses, glass, packedLight, OverlayTexture.NO_OVERLAY);
        poses.popPose();
    }

    private static void applyLegacyEquippedBridge(ItemDisplayContext context, PoseStack poses) {
        float side = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -1.0F : 1.0F;
        poses.translate(-side / 16.0D, -0.125D, 0.625D);
        poses.mulPose(Axis.YN.rotationDegrees(180.0F));
        poses.mulPose(Axis.XP.rotationDegrees(90.0F));
        poses.translate(-side / 16.0D, 0.4375D, 0.0625D);
        poses.translate(side * 0.25D, 0.1875D, -0.1875D);
        poses.scale(0.375F, 0.375F, 0.375F);
        poses.mulPose(Axis.ZP.rotationDegrees(side * 60.0F));
        poses.mulPose(Axis.XN.rotationDegrees(90.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(side * 20.0F));
        poses.translate(0.0D, -0.3D, 0.0D);
        poses.scale(1.5F, 1.5F, 1.5F);
        poses.mulPose(Axis.YP.rotationDegrees(side * 50.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(side * 335.0F));
        poses.translate(-side * 0.9375D, -0.0625D, 0.0D);
    }

    private static void renderIcon(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel baked = minecraft.getModelManager().getModel(ICON);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        renderer.renderModel(poses.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                Blocks.IRON_BLOCK.defaultBlockState(), baked, 1.0F, 1.0F, 1.0F, light, overlay);
    }
}
