package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Original ArmorEnvsuit OBJ renderer for inventory, held, fixed, and dropped items. */
public final class EnvsuitItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation HELMET_TEXTURE = texture("envsuit_helmet");
    private static final ResourceLocation CHEST_TEXTURE = texture("envsuit_chest");
    private static final ResourceLocation ARM_TEXTURE = texture("envsuit_arm");
    private static final ResourceLocation LEG_TEXTURE = texture("envsuit_leg");

    private static final RenderType UNTEXTURED_LAMPS = RenderType.create(
            "hbm_envsuit_item_lamps", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 1_024, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    private final ArmorItem.Type piece;
    private EnvsuitMesh mesh;

    public EnvsuitItemRenderer(ArmorItem.Type piece) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
        this.piece = piece;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        // ItemRenderer moves every BEWLR by -0.5 after applying the baked display
        // transform. The 1.7 IItemRenderer worked from its context origin.
        poses.translate(0.5D, 0.5D, 0.5D);

        switch (context) {
            case GUI -> setupInventory(poses);
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> setupThirdPerson(context, poses);
            case GROUND -> setupNonInventory(poses, true, true, 1.0F);
            case FIRST_PERSON_LEFT_HAND -> setupNonInventory(poses, false, true, -1.0F);
            case FIRST_PERSON_RIGHT_HAND -> setupNonInventory(poses, false, true, 1.0F);
            case FIXED, HEAD, NONE -> setupNonInventory(poses, false, true, 1.0F);
        }

        renderPiece(poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    static void setupInventory(PoseStack poses) {
        setupInventory(poses, 0.0D);
    }

    static void setupInventory(PoseStack poses, double legacyYOffset) {
        // Forge 1.7 supplied x/y as the slot's top-left. Modern GUI rendering enters
        // the BEWLR at the slot center with Y reflected, so bridge those conventions
        // before replaying ItemRenderBase and ArmorFSB.setupRenderInv literally.
        poses.scale(1.0F, -1.0F, 1.0F);
        poses.translate(0.0D, 2.0D / 16.0D, 0.0D);
        poses.mulPose(Axis.XN.rotationDegrees(30.0F));
        poses.mulPose(Axis.YP.rotationDegrees(45.0F));
        poses.scale(-1.0F / 16.0F, -1.0F / 16.0F, -1.0F / 16.0F);

        if (legacyYOffset != 0.0D) poses.translate(0.0D, legacyYOffset, 0.0D);
        poses.translate(0.0D, -1.5D, 0.0D);
        poses.scale(3.25F, 3.25F, 3.25F);
        poses.mulPose(Axis.XP.rotationDegrees(180.0F));
        poses.mulPose(Axis.YN.rotationDegrees(135.0F));
        poses.mulPose(Axis.XN.rotationDegrees(20.0F));
    }

    static void setupThirdPerson(ItemDisplayContext context, PoseStack poses) {
        boolean left = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        float side = left ? -1.0F : 1.0F;

        // Undo the post-arm item matrix while keeping the arm's animated
        // transform, then reconstruct RenderPlayer's 1.7 held-item path.
        poses.translate(-side / 16.0D, -0.125D, 0.625D);
        poses.mulPose(Axis.YN.rotationDegrees(180.0F));
        poses.mulPose(Axis.XP.rotationDegrees(90.0F));
        poses.translate(-side / 16.0D, 0.4375D, 0.0625D);

        // ItemArmor was not full-3D, so RenderPlayer used its flat-item matrix.
        poses.translate(side * 0.25D, 0.1875D, -0.1875D);
        poses.scale(0.375F, 0.375F, 0.375F);
        poses.mulPose(Axis.ZP.rotationDegrees(side * 60.0F));
        poses.mulPose(Axis.XN.rotationDegrees(90.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(side * 20.0F));

        // ForgeHooksClient.renderEquippedItem's non-block custom-renderer helper.
        poses.translate(0.0D, -0.3D, 0.0D);
        poses.scale(1.5F, 1.5F, 1.5F);
        poses.mulPose(Axis.YP.rotationDegrees(side * 50.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(side * 335.0F));
        poses.translate(-side * 0.9375D, -0.0625D, 0.0D);

        setupNonInventory(poses, false, false, side);
    }

    static void setupNonInventory(PoseStack poses, boolean entity,
                                          boolean rotateAsNonEquipped, float side) {
        if (entity) poses.scale(1.5F, 1.5F, 1.5F);
        else poses.translate(side * 0.5D, 0.25D, 0.0D);
        poses.scale(0.25F, 0.25F, 0.25F);
        if (rotateAsNonEquipped) poses.mulPose(Axis.YP.rotationDegrees(side * 90.0F));

        poses.mulPose(Axis.XP.rotationDegrees(180.0F));
        poses.scale(0.75F, 0.75F, 0.75F);
        poses.mulPose(Axis.YN.rotationDegrees(side * 90.0F));
    }

    private void renderPiece(PoseStack poses, MultiBufferSource buffers,
                             int packedLight, int packedOverlay) {
        switch (piece) {
            case HELMET -> {
                poses.scale(0.3125F, 0.3125F, 0.3125F);
                poses.translate(0.0D, 1.0D, 0.0D);
                renderTextured("Helmet", HELMET_TEXTURE, true, poses, buffers, packedLight, packedOverlay);
                renderLamps(poses, buffers);
            }
            case CHESTPLATE -> {
                poses.scale(0.225F, 0.225F, 0.225F);
                poses.translate(0.0D, -10.0D, 0.0D);
                renderTextured("Chest", CHEST_TEXTURE, true, poses, buffers, packedLight, packedOverlay);
                poses.translate(0.0D, 0.0D, 0.1D);
                renderTextured("LeftArm", ARM_TEXTURE, true, poses, buffers, packedLight, packedOverlay);
                renderTextured("RightArm", ARM_TEXTURE, true, poses, buffers, packedLight, packedOverlay);
            }
            case LEGGINGS -> {
                poses.scale(0.25F, 0.25F, 0.25F);
                poses.translate(0.0D, -20.0D, 0.0D);
                renderTextured("LeftLeg", LEG_TEXTURE, false, poses, buffers, packedLight, packedOverlay);
                poses.translate(0.0D, 0.0D, 0.1D);
                renderTextured("RightLeg", LEG_TEXTURE, false, poses, buffers, packedLight, packedOverlay);
            }
            case BOOTS -> {
                poses.scale(0.25F, 0.25F, 0.25F);
                poses.translate(0.0D, -22.0D, 0.0D);
                renderTextured("LeftFoot", LEG_TEXTURE, false, poses, buffers, packedLight, packedOverlay);
                poses.translate(0.0D, 0.0D, 0.1D);
                renderTextured("RightFoot", LEG_TEXTURE, false, poses, buffers, packedLight, packedOverlay);
            }
            default -> {
            }
        }
    }

    private void renderTextured(String group, ResourceLocation texture, boolean cull,
                                PoseStack poses, MultiBufferSource buffers,
                                int light, int overlay) {
        RenderType renderType = cull ? RenderType.entityCutout(texture) : RenderType.entityCutoutNoCull(texture);
        mesh().render(group, poses.last(), buffers.getBuffer(renderType), 1.0F, light, overlay, 0xFFFFFFFF);
    }

    private void renderLamps(PoseStack poses, MultiBufferSource buffers) {
        VertexConsumer consumer = buffers.getBuffer(UNTEXTURED_LAMPS);
        PoseStack.Pose pose = poses.last();
        for (EnvsuitMesh.Vertex vertex : mesh().group("Lamps")) {
            consumer.addVertex(pose, vertex.x(), vertex.y(), vertex.z())
                    .setColor(1.0F, 1.0F, 0.8F, 1.0F);
        }
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager());
        return mesh;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "textures/armor/" + name + ".png");
    }
}
