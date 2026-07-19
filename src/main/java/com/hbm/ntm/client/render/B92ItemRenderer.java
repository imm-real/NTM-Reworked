package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.B92Model;
import com.hbm.ntm.item.B92Item;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** B92 gun animation wrapped around a model made of sensible parts. */
public final class B92ItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation ICON = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "item/gun_b92_icon"));
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/models/model_b92sm.png");
    private final B92Model model;

    public B92ItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        model = new B92Model(Minecraft.getInstance().getEntityModels().bakeLayer(B92Model.LAYER));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        if (context == ItemDisplayContext.GUI) {
            // ItemRenderGunAnim.handleRenderType returned false for INVENTORY, so 1.7.10
            // drew gun_b92.png through the ordinary item renderer. Keep ItemRenderer's
            // existing -0.5 centering when drawing this generated 0..1 model; cancelling
            // it places the sprite in the upper-right quadrant of the slot.
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
            float rotation = B92Item.rotationFromAnimation(stack);
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

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        model.render(poses, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                B92Item.translationFromAnimation(stack));
        poses.popPose();
    }

    public static void applyLegacyFirstPersonTransform(PoseStack poses, LocalPlayer player,
                                                       HumanoidArm arm, ItemStack stack,
                                                       float partialTick, float equipProgress,
                                                       float swingProgress) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;

        // Minecraft 1.7's arm-follow sway ran before the held-item transforms.
        float armPitch = Mth.lerp(partialTick, player.xBobO, player.xBob);
        float armYaw = Mth.lerp(partialTick, player.yBobO, player.yBob);
        poses.mulPose(Axis.XP.rotationDegrees((player.getXRot() - armPitch) * 0.1F));
        poses.mulPose(Axis.YP.rotationDegrees((player.getYRot() - armYaw) * 0.1F));

        boolean using = player.isUsingItem() && player.getUseItem() == stack;
        float swingSquared = Mth.sin(swingProgress * swingProgress * Mth.PI);
        float swingRoot = Mth.sin(Mth.sqrt(swingProgress) * Mth.PI);
        if (!using) {
            poses.translate(side * -swingRoot * 0.4F,
                    Mth.sin(Mth.sqrt(swingProgress) * Mth.PI * 2.0F) * 0.2F,
                    -Mth.sin(swingProgress * Mth.PI) * 0.2F);
        }

        // ItemRenderer.renderItemInFirstPerson's ordinary item placement. NeoForge's
        // equipProgress runs backwards here. Naturally.
        poses.translate(side * 0.56F, -0.52F - equipProgress * 0.6F, -0.72F);
        poses.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
        poses.mulPose(Axis.YP.rotationDegrees(side * -swingSquared * 20.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(side * -swingRoot * 20.0F));
        poses.mulPose(Axis.XP.rotationDegrees(-swingRoot * 80.0F));
        poses.scale(0.4F, 0.4F, 0.4F);

        if (using) {
            poses.mulPose(Axis.ZP.rotationDegrees(side * -18.0F));
            poses.mulPose(Axis.YP.rotationDegrees(side * -12.0F));
            poses.mulPose(Axis.XP.rotationDegrees(-8.0F));
            poses.translate(side * -0.9F, 0.2F, 0.0F);

            float useTicks = stack.getUseDuration(player)
                    - (player.getUseItemRemainingTicks() - partialTick + 1.0F);
            float power = useTicks / 20.0F;
            power = (power * power + power * 2.0F) / 3.0F;
            power = Math.min(power, 1.0F);
            if (power > 0.1F) {
                poses.translate(0.0F,
                        Mth.sin((useTicks - 0.1F) * 1.3F) * (power - 0.1F) * 0.01F,
                        0.0F);
            }
            poses.translate(0.0F, 0.0F, power * 0.1F);
            poses.mulPose(Axis.ZP.rotationDegrees(side * -335.0F));
            poses.mulPose(Axis.YP.rotationDegrees(side * -50.0F));
            poses.translate(0.0F, 0.5F, 0.0F);
            poses.scale(1.0F, 1.0F, 1.0F + power * 0.2F);
            poses.translate(0.0F, -0.5F, 0.0F);
            poses.mulPose(Axis.YP.rotationDegrees(side * 50.0F));
            poses.mulPose(Axis.ZP.rotationDegrees(side * 335.0F));
        }

        // ForgeHooksClient.renderEquippedItem's non-block helper, which wrapped the
        // custom ItemRenderGunAnim call for EQUIPPED_FIRST_PERSON.
        poses.translate(0.0F, -0.3F, 0.0F);
        poses.scale(1.5F, 1.5F, 1.5F);
        poses.mulPose(Axis.YP.rotationDegrees(side * 50.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(side * 335.0F));
        poses.translate(side * -0.9375F, -0.0625F, 0.0F);
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
