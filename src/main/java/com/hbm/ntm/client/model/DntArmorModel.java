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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;

import java.util.Set;

/** Bolts the DNT armor OBJ onto the correct number of limbs. */
public final class DntArmorModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "dnt_nano_suit"), "main");
    public static final int HELMET_PASS = 0;
    public static final int CHEST_PASS = 1;
    public static final int ARM_PASS = 2;
    public static final int LEG_PASS = 3;
    private static final int FULL_PIECE_PASS = -2;
    private static final float SCALE = 1.0F / 16.0F;
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "models/armor/dnt.obj");
    private static final Set<String> GROUPS = Set.of(
            "Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg", "LeftBoot", "RightBoot");

    private final ArmorItem.Type piece;
    private final ResourceManager resources;
    private EnvsuitMesh mesh;
    private int activePass = -1;

    public DntArmorModel(ModelPart root, ResourceManager resources, ArmorItem.Type piece) {
        super(root);
        this.resources = resources;
        this.piece = piece;
    }

    public void beginRender() {
        activePass = -1;
    }

    public boolean usesPass(int pass) {
        return switch (piece) {
            case HELMET -> pass == HELMET_PASS;
            case CHESTPLATE -> pass == CHEST_PASS || pass == ARM_PASS;
            case LEGGINGS, BOOTS -> pass == LEG_PASS;
            default -> false;
        };
    }

    public void setPass(int pass) {
        activePass = pass;
    }

    @Override
    public void renderToBuffer(PoseStack poses, VertexConsumer consumer, int light, int overlay, int color) {
        switch (activePass) {
            case HELMET_PASS -> { if (piece == ArmorItem.Type.HELMET) renderHead(poses, consumer, light, overlay, color); }
            case CHEST_PASS -> { if (piece == ArmorItem.Type.CHESTPLATE) renderBody(poses, consumer, light, overlay, color); }
            case ARM_PASS -> { if (piece == ArmorItem.Type.CHESTPLATE) renderArms(poses, consumer, light, overlay, color); }
            case LEG_PASS -> {
                if (piece == ArmorItem.Type.LEGGINGS) renderLegs(poses, consumer, light, overlay, color);
                if (piece == ArmorItem.Type.BOOTS) renderBoots(poses, consumer, light, overlay, color);
            }
            case FULL_PIECE_PASS -> renderFullPiece(poses, consumer, light, overlay, color);
            default -> { }
        }
        if (activePass == lastPass()) activePass = FULL_PIECE_PASS;
    }

    private void renderFullPiece(PoseStack poses, VertexConsumer consumer, int light, int overlay, int color) {
        switch (piece) {
            case HELMET -> renderHead(poses, consumer, light, overlay, color);
            case CHESTPLATE -> { renderBody(poses, consumer, light, overlay, color); renderArms(poses, consumer, light, overlay, color); }
            case LEGGINGS -> renderLegs(poses, consumer, light, overlay, color);
            case BOOTS -> renderBoots(poses, consumer, light, overlay, color);
            default -> { }
        }
    }

    private void renderHead(PoseStack p, VertexConsumer c, int l, int o, int color) {
        renderAttached("Head", head, 0, 0, p, c, l, o, color);
    }

    private void renderBody(PoseStack p, VertexConsumer c, int l, int o, int color) {
        renderAttached("Body", body, 0, 0, p, c, l, o, color);
    }

    private void renderArms(PoseStack p, VertexConsumer c, int l, int o, int color) {
        renderAttached("LeftArm", leftArm, 5.0F, 2.0F, p, c, l, o, color);
        renderAttached("RightArm", rightArm, -5.0F, 2.0F, p, c, l, o, color);
    }

    private void renderLegs(PoseStack p, VertexConsumer c, int l, int o, int color) {
        renderAttached("LeftLeg", leftLeg, 1.9F, 12.0F, p, c, l, o, color);
        renderAttached("RightLeg", rightLeg, -1.9F, 12.0F, p, c, l, o, color);
    }

    private void renderBoots(PoseStack p, VertexConsumer c, int l, int o, int color) {
        renderAttached("LeftBoot", leftLeg, 1.9F, 12.0F, p, c, l, o, color);
        renderAttached("RightBoot", rightLeg, -1.9F, 12.0F, p, c, l, o, color);
    }

    private void renderAttached(String group, ModelPart parent, float x, float y,
                                PoseStack poses, VertexConsumer consumer, int light, int overlay, int color) {
        poses.pushPose();
        parent.translateAndRotate(poses);
        poses.translate(-x * SCALE, -y * SCALE, 0.0D);
        mesh().render(group, poses.last(), consumer, SCALE, light, overlay, color);
        poses.popPose();
    }

    private int lastPass() {
        return switch (piece) {
            case HELMET -> HELMET_PASS;
            case CHESTPLATE -> ARM_PASS;
            case LEGGINGS, BOOTS -> LEG_PASS;
            default -> -1;
        };
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) mesh = EnvsuitMesh.load(resources, MODEL, GROUPS, "DNT Nano Suit");
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
