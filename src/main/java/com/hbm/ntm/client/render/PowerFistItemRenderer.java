package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.PowerFistModel;
import com.hbm.ntm.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Power Fist renderer, featuring one locally sourced Steve arm. */
public final class PowerFistItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation FIST_ICON = icon("multitool_fist_icon");
    public static final ModelResourceLocation CLAW_ICON = icon("multitool_claw_icon");
    public static final ModelResourceLocation OPEN_ICON = icon("multitool_open_icon");
    public static final ModelResourceLocation POINTER_ICON = icon("multitool_pointer_icon");

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/models/model_multitool.png");
    private static final ResourceLocation STEVE = ResourceLocation.withDefaultNamespace(
            "textures/entity/player/wide/steve.png");

    private final PowerFistModel fist;
    private final PowerFistModel claw;
    private final PowerFistModel open;
    private final PowerFistModel pointer;
    private final ModelPart steveRightArm;

    public PowerFistItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        fist = new PowerFistModel(Minecraft.getInstance().getEntityModels().bakeLayer(PowerFistModel.FIST_LAYER));
        claw = new PowerFistModel(Minecraft.getInstance().getEntityModels().bakeLayer(PowerFistModel.CLAW_LAYER));
        open = new PowerFistModel(Minecraft.getInstance().getEntityModels().bakeLayer(PowerFistModel.OPEN_LAYER));
        pointer = new PowerFistModel(Minecraft.getInstance().getEntityModels().bakeLayer(PowerFistModel.POINTER_LAYER));
        steveRightArm = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER).getChild("right_arm");
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        if (context == ItemDisplayContext.GUI) {
            // Inventory gets the icon. Steve's arm would not fit in the slot.
            renderIcon(stack, iconFor(stack), poses, buffers, packedLight, packedOverlay);
            poses.popPose();
            return;
        }

        // Repay the half-block loan from ItemRenderer.
        poses.translate(0.5D, 0.5D, 0.5D);
        if (context == ItemDisplayContext.GROUND) {
            // Ground items get extra bob and height now. Remove both taxes.
            poses.translate(0.0D, -0.25D, 0.0D);
            poses.scale(0.5F, 0.5F, 0.5F);
        }
        if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            renderFirstPerson(stack, poses, buffers, packedLight);
        } else {
            if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                applyLegacyEquippedBridge(context, poses);
            }
            renderEquippedOrEntity(stack, poses, buffers, packedLight);
        }
        poses.popPose();
    }

    private void renderFirstPerson(ItemStack stack, PoseStack poses, MultiBufferSource buffers, int light) {
        poses.mulPose(Axis.ZN.rotationDegrees(135.0F));
        poses.translate(-0.5D, 0.0D, -0.2D);
        poses.scale(0.5F, 0.5F, 0.5F);
        poses.translate(-0.2D, -0.1D, -0.1D);
        renderModel(stack, poses, buffers, light);

        // Yes, that is Steve's arm. No, you may not have your own.
        poses.scale(2.0F, 2.0F, 2.0F);
        poses.mulPose(Axis.ZP.rotationDegrees(90.0F));
        poses.translate(6.0D / 16.0D, -12.0D / 16.0D, 0.0D);
        VertexConsumer arm = buffers.getBuffer(RenderType.entityCutout(STEVE));
        steveRightArm.render(poses, arm, light, OverlayTexture.NO_OVERLAY);
    }

    private void renderEquippedOrEntity(ItemStack stack, PoseStack poses,
                                        MultiBufferSource buffers, int light) {
        poses.scale(0.75F, 0.75F, 0.75F);
        poses.mulPose(Axis.ZN.rotationDegrees(200.0F));
        poses.mulPose(Axis.YP.rotationDegrees(75.0F));
        poses.mulPose(Axis.XN.rotationDegrees(30.0F));
        poses.mulPose(Axis.ZN.rotationDegrees(5.0F));
        poses.translate(-4.0D / 16.0D, 2.0D / 16.0D, -9.0D / 16.0D);
        renderModel(stack, poses, buffers, light);
    }

    private void renderModel(ItemStack stack, PoseStack poses, MultiBufferSource buffers, int light) {
        RenderType type = RenderType.entityCutout(TEXTURE);
        // Glint stays in the inventory where it cannot hurt the OBJ.
        VertexConsumer consumer = buffers.getBuffer(type);
        modelFor(stack).render(poses, consumer, light, OverlayTexture.NO_OVERLAY);
    }

    private PowerFistModel modelFor(ItemStack stack) {
        if (stack.is(ModItems.MULTITOOL_DIG.get()) || stack.is(ModItems.MULTITOOL_SILK.get())) return claw;
        if (stack.is(ModItems.MULTITOOL_EXT.get()) || stack.is(ModItems.MULTITOOL_SKY.get())
                || stack.is(ModItems.MULTITOOL_DECON.get())) return open;
        if (stack.is(ModItems.MULTITOOL_MINER.get()) || stack.is(ModItems.MULTITOOL_BEAM.get())) return pointer;
        return fist;
    }

    private static ModelResourceLocation iconFor(ItemStack stack) {
        if (stack.is(ModItems.MULTITOOL_DIG.get()) || stack.is(ModItems.MULTITOOL_SILK.get())) return CLAW_ICON;
        // Decontaminator lies about its hand shape in the inventory.
        if (stack.is(ModItems.MULTITOOL_EXT.get()) || stack.is(ModItems.MULTITOOL_SKY.get())) return OPEN_ICON;
        if (stack.is(ModItems.MULTITOOL_MINER.get()) || stack.is(ModItems.MULTITOOL_BEAM.get())) return POINTER_ICON;
        return FIST_ICON;
    }

    private static void renderIcon(ItemStack stack, ModelResourceLocation icon, PoseStack poses,
                                   MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel baked = minecraft.getModelManager().getModel(icon);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(
                buffers, Sheets.cutoutBlockSheet(), true, stack.hasFoil());
        renderer.renderModel(poses.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), baked,
                1.0F, 1.0F, 1.0F, light, overlay);
    }

    /** Old equipped-item matrix, excavated with the B92. */
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

    private static ModelResourceLocation icon(String path) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "item/" + path));
    }
}
