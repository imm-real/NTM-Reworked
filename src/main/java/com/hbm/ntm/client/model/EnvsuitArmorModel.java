package com.hbm.ntm.client.model;

import com.hbm.ntm.HbmNtm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;

/** M1TTY suit, assembled one texture pass at a time. */
public final class EnvsuitArmorModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "envsuit"), "main");

    public static final int HELMET_PASS = 0;
    public static final int CHEST_PASS = 1;
    public static final int ARM_PASS = 2;
    public static final int LEG_PASS = 3;
    public static final int LAMP_PASS = 4;

    private static final int FULL_PIECE_PASS = -2;
    private static final float MODEL_SCALE = 1.0F / 16.0F;

    // One white helmet pixel, stretched into a lamp. Resourcefulness.
    private static final float LAMP_U = 59.5F / 72.0F;
    private static final float LAMP_V = 15.5F / 61.0F;

    private final ArmorItem.Type piece;
    private final ResourceManager resources;
    private EnvsuitMesh mesh;
    private int activePass = -1;

    public EnvsuitArmorModel(ModelPart root, ResourceManager resources, ArmorItem.Type piece) {
        super(root);
        this.resources = resources;
        this.piece = piece;
    }

    public void beginRender() {
        activePass = -1;
    }

    public boolean usesPass(int pass) {
        return switch (piece) {
            case HELMET -> pass == HELMET_PASS || pass == LAMP_PASS;
            case CHESTPLATE -> pass == CHEST_PASS || pass == ARM_PASS;
            case LEGGINGS, BOOTS -> pass == LEG_PASS;
            default -> false;
        };
    }

    public void setPass(int pass) {
        activePass = pass;
    }

    @Override
    public void renderToBuffer(PoseStack poses, VertexConsumer consumer, int packedLight,
                               int packedOverlay, int color) {
        switch (activePass) {
            case HELMET_PASS -> {
                if (piece == ArmorItem.Type.HELMET) renderHead(poses, consumer, packedLight, packedOverlay, color);
            }
            case CHEST_PASS -> {
                if (piece == ArmorItem.Type.CHESTPLATE) renderBody(poses, consumer, packedLight, packedOverlay, color);
            }
            case ARM_PASS -> {
                if (piece == ArmorItem.Type.CHESTPLATE) renderArms(poses, consumer, packedLight, packedOverlay, color);
            }
            case LEG_PASS -> {
                if (piece == ArmorItem.Type.LEGGINGS) renderLegs(poses, consumer, packedLight, packedOverlay, color);
                if (piece == ArmorItem.Type.BOOTS) renderFeet(poses, consumer, packedLight, packedOverlay, color);
            }
            case LAMP_PASS -> {
                if (piece == ArmorItem.Type.HELMET) renderLamps(poses, consumer, packedOverlay, color);
            }
            case FULL_PIECE_PASS -> renderFullPiece(poses, consumer, packedLight, packedOverlay, color);
            default -> {
            }
        }

        // Trims and glint arrive late and need the whole outfit.
        if (activePass == lastTexturePass()) activePass = FULL_PIECE_PASS;
    }

    private void renderFullPiece(PoseStack poses, VertexConsumer consumer, int packedLight,
                                 int packedOverlay, int color) {
        switch (piece) {
            case HELMET -> {
                renderHead(poses, consumer, packedLight, packedOverlay, color);
                renderLamps(poses, consumer, packedOverlay, color);
            }
            case CHESTPLATE -> {
                renderBody(poses, consumer, packedLight, packedOverlay, color);
                renderArms(poses, consumer, packedLight, packedOverlay, color);
            }
            case LEGGINGS -> renderLegs(poses, consumer, packedLight, packedOverlay, color);
            case BOOTS -> renderFeet(poses, consumer, packedLight, packedOverlay, color);
            default -> {
            }
        }
    }

    private void renderHead(PoseStack poses, VertexConsumer consumer, int light, int overlay, int color) {
        renderAttached("Helmet", head, 0.0F, 0.0F, 0.0F,
                poses, consumer, light, overlay, color, false);
    }

    private void renderLamps(PoseStack poses, VertexConsumer consumer, int overlay, int color) {
        renderAttached("Lamps", head, 0.0F, 0.0F, 0.0F,
                poses, consumer, LightTexture.FULL_BRIGHT, overlay, color, true);
    }

    private void renderBody(PoseStack poses, VertexConsumer consumer, int light, int overlay, int color) {
        renderAttached("Chest", body, 0.0F, 0.0F, 0.0F,
                poses, consumer, light, overlay, color, false);
    }

    private void renderArms(PoseStack poses, VertexConsumer consumer, int light, int overlay, int color) {
        renderAttached("LeftArm", leftArm, 5.0F, 2.0F, 0.0F,
                poses, consumer, light, overlay, color, false);
        renderAttached("RightArm", rightArm, -5.0F, 2.0F, 0.0F,
                poses, consumer, light, overlay, color, false);
    }

    private void renderLegs(PoseStack poses, VertexConsumer consumer, int light, int overlay, int color) {
        renderAttached("LeftLeg", leftLeg, 1.9F, 12.0F, 0.0F,
                poses, consumer, light, overlay, color, false);
        renderAttached("RightLeg", rightLeg, -1.9F, 12.0F, 0.0F,
                poses, consumer, light, overlay, color, false);
    }

    private void renderFeet(PoseStack poses, VertexConsumer consumer, int light, int overlay, int color) {
        renderAttached("LeftFoot", leftLeg, 1.9F, 12.0F, 0.0F,
                poses, consumer, light, overlay, color, false);
        renderAttached("RightFoot", rightLeg, -1.9F, 12.0F, 0.0F,
                poses, consumer, light, overlay, color, false);
    }

    private void renderAttached(String group, ModelPart parent,
                                float originX, float originY, float originZ,
                                PoseStack poses, VertexConsumer consumer,
                                int light, int overlay, int color, boolean lampUv) {
        poses.pushPose();
        parent.translateAndRotate(poses);
        poses.translate(-originX * MODEL_SCALE, -originY * MODEL_SCALE, -originZ * MODEL_SCALE);
        if (lampUv) {
            mesh().renderWithUv(group, poses.last(), consumer, MODEL_SCALE,
                    light, overlay, color, LAMP_U, LAMP_V);
        } else {
            mesh().render(group, poses.last(), consumer, MODEL_SCALE, light, overlay, color);
        }
        poses.popPose();
    }

    private int lastTexturePass() {
        return switch (piece) {
            case HELMET -> LAMP_PASS;
            case CHESTPLATE -> ARM_PASS;
            case LEGGINGS, BOOTS -> LEG_PASS;
            default -> -1;
        };
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) mesh = EnvsuitMesh.load(resources);
        return mesh;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
