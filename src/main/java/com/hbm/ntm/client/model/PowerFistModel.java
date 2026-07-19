package com.hbm.ntm.client.model;

import com.hbm.ntm.HbmNtm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/** Four Power Fists from the golden age of Techne cubes. */
public final class PowerFistModel {
    public static final ModelLayerLocation FIST_LAYER = layer("power_fist_fist");
    public static final ModelLayerLocation CLAW_LAYER = layer("power_fist_claw");
    public static final ModelLayerLocation OPEN_LAYER = layer("power_fist_open");
    public static final ModelLayerLocation POINTER_LAYER = layer("power_fist_pointer");

    private final ModelPart root;

    public PowerFistModel(ModelPart root) {
        this.root = root;
    }

    public void render(PoseStack poses, VertexConsumer consumer, int light, int overlay) {
        root.render(poses, consumer, light, overlay);
    }

    public static LayerDefinition createFistLayer() {
        return createLayer(Variant.FIST);
    }

    public static LayerDefinition createClawLayer() {
        return createLayer(Variant.CLAW);
    }

    public static LayerDefinition createOpenLayer() {
        return createLayer(Variant.OPEN);
    }

    public static LayerDefinition createPointerLayer() {
        return createLayer(Variant.POINTER);
    }

    private static LayerDefinition createLayer(Variant variant) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        part(root, "Base", 0, 0, 0, 0, 0, 3, 8, 8, -3, -4, -4, 0, 0, 0);
        part(root, "BTop", 0, 16, 0, 0, 0, 4, 2, 8, -3, -4, -4, 0, 0, -0.2617994F);
        part(root, "BBottom", 0, 26, 0, -2, 0, 4, 2, 8, -3, 4, -4, 0, 0, 0.2617994F);
        part(root, "BLeft", 0, 36, 0, 0, 0, 4, 8, 2, -3, -4, -4, 0, 0.2617994F, 0);
        part(root, "BRight", 12, 36, 0, 0, -2, 4, 8, 2, -3, -4, 4, 0, -0.2617994F, 0);
        part(root, "RTop", 24, 0, 0, 0, 0, 3, 2, 10, 4, -6, -6, 0, 0, 0);
        part(root, "RBottom", 24, 12, 0, 0, 0, 3, 2, 10, 4, 4, -4, 0, 0, 0);
        part(root, "RLeft", 0, 46, 0, 0, 0, 3, 10, 2, 4, -4, -6, 0, 0, 0);
        part(root, "RRight", 10, 46, 0, 0, 0, 3, 10, 2, 4, -6, 4, 0, 0, 0);
        part(root, "GPivot", 24, 24, 0, 0, 0, 3, 4, 4, -6, -2, -2, 0, 0, 0);

        switch (variant) {
            case FIST -> fistParts(root);
            case CLAW -> clawParts(root);
            case OPEN -> openParts(root);
            case POINTER -> pointerParts(root);
        }

        part(root, "WireL", 38, 30, 0, 0, 0, 4, 1, 1, 0, -5.5F, 0, 0, 0, 0);
        part(root, "WireR", 38, 28, 0, 0, 0, 4, 1, 1, 0, -5.5F, 2, 0, 0, 0);
        part(root, "Gauge1", 20, 47, -1.5F, -1, -2, 3, 1, 4,
                -1, -4, 4, -0.7853982F, 0, 0);
        part(root, "Gauge2", 34, 48, -2, -1, -1.5F, 4, 1, 3,
                -1, -4, 4, -0.7853982F, 0, 0);
        part(root, "WireB", 48, 49, 0, 0, 0, 4, 2, 1, 0, -1, -5.5F, 0, 0, 0);

        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void fistParts(PartDefinition root) {
        part(root, "GBase", 24, 32, -2, -3, -4, 4, 3, 8, -6, 0, 1, 0, 0, 0.6108652F);
        part(root, "F31", 20, 52, -3, -1, 0, 3, 2, 2, -6, -2.8F, -1, 0, 0, -0.5235988F);
        part(root, "F21", 30, 52, -3, -1, -2, 3, 2, 2, -6, -2.8F, -1.2F, 0, 0, -0.5235988F);
        part(root, "F41", 40, 52, -3, -1, 0, 3, 2, 2, -6, -2.8F, 1.2F, 0, 0, -0.5235988F);
        part(root, "F51", 50, 52, -3, -1, 0, 3, 2, 2, -6, -2.8F, 3.4F, 0, 0, -0.5235988F);
        part(root, "F11", 48, 38, -1, -1, -3, 2, 2, 3, -5, -1, -2.5F, 1.22173F, 1.745329F, -1.047198F);
        part(root, "F22", 20, 56, -3, -1, -1, 3, 2, 2, -8.5F, -2, -2.2F, 0, 0, -1.919862F);
        part(root, "F32", 30, 56, -3, -1, -1, 3, 2, 2, -8.5F, -2, 0, 0, 0, -1.919862F);
        part(root, "F42", 40, 56, -3, -1, -1, 3, 2, 2, -8.5F, -2, 2.2F, 0, 0, -1.919862F);
        part(root, "F52", 50, 56, -3, -1, -1, 3, 2, 2, -8.5F, -2, 4.4F, 0, 0, -1.919862F);
        part(root, "F12", 48, 34, -1, -1, -2, 2, 2, 2, -6, 0.5F, -4.5F, 1.22173F, 2.935045F, -1.047198F);
        part(root, "F23", 20, 60, -3, -1, -1, 3, 2, 2, -8, 0.5F, -2.2F, 0, 0, -2.879793F);
        part(root, "F33", 30, 60, -3, -1, -1, 3, 2, 2, -8, 0.5F, 0, 0, 0, -2.879793F);
        part(root, "F43", 40, 60, -3, -1, -1, 3, 2, 2, -8, 0.5F, 2.2F, 0, 0, -2.879793F);
        part(root, "F53", 50, 60, -3, -1, -1, 3, 2, 2, -8, 0.5F, 4.4F, 0, 0, -2.879793F);
        part(root, "F13", 48, 30, -1, -1, -2, 2, 2, 2, -7, 1, -4, 0.5235988F, 2.617994F, -1.047198F);
    }

    private static void clawParts(PartDefinition root) {
        part(root, "GBase", 24, 32, -2, -3, -4, 4, 3, 8, -6, 0, 1, 0, 0, 1.047198F);
        part(root, "F31", 20, 52, -3, -1, 0, 3, 2, 2, -5.5F, -2, -1, 0, 0, 0.6981317F);
        part(root, "F21", 30, 52, -3, -1, -2, 3, 2, 2, -5.5F, -2, -1.2F, 0, 0, 0.6981317F);
        part(root, "F41", 40, 52, -3, -1, 0, 3, 2, 2, -5.5F, -2, 1.2F, 0, 0, 0.6981317F);
        part(root, "F51", 50, 52, -3, -1, 0, 3, 2, 2, -5.5F, -2, 3.4F, 0, 0, 0.6981317F);
        part(root, "F11", 48, 38, 0, -1, -3, 2, 2, 3, -5.5F, -2, -3, 0, 0, 1.047198F);
        part(root, "F22", 20, 56, -3, -1, -1, 3, 2, 2, -7.6F, -3.7F, -2.2F, 0, 0, 0.3490659F);
        part(root, "F32", 30, 56, -3, -1, -1, 3, 2, 2, -7.6F, -3.7F, 0, 0, 0, 0.3490659F);
        part(root, "F42", 40, 56, -3, -1, -1, 3, 2, 2, -7.6F, -3.7F, 2.2F, 0, 0, 0.3490659F);
        part(root, "F52", 50, 56, -3, -1, -1, 3, 2, 2, -7.6F, -3.7F, 4.4F, 0, 0, 0.3490659F);
        part(root, "F12", 48, 34, -1, -1, -2, 2, 2, 2, -5, -1, -5.8F, 0, 0.7853982F, 1.047198F);
        part(root, "F23", 20, 60, -3, -1, -1, 3, 2, 2, -10, -4.6F, -2.2F, 0, 0, -0.1745329F);
        part(root, "F33", 30, 60, -3, -1, -1, 3, 2, 2, -10, -4.6F, 0, 0, 0, -0.1745329F);
        part(root, "F43", 40, 60, -3, -1, -1, 3, 2, 2, -10, -4.6F, 2.2F, 0, 0, -0.1745329F);
        part(root, "F53", 50, 60, -3, -1, -1, 3, 2, 2, -10, -4.6F, 4.4F, 0, 0, -0.1745329F);
        part(root, "F13", 48, 30, -1, -1, -2, 2, 2, 2, -5.5F, -1, -7.2F, 0.6981317F, 1.047198F, 1.047198F);
    }

    private static void openParts(PartDefinition root) {
        part(root, "GBase", 24, 32, -2, -3, -4, 4, 3, 8, -6, 0, 1, 0, 0, 1.047198F);
        part(root, "F31", 20, 52, -3, -1, 0, 3, 2, 2, -5.5F, -2, -1, 0, 0, 1.48353F);
        part(root, "F21", 30, 52, -3, -1, -2, 3, 2, 2, -5.5F, -2, -1.2F, 0, 0, 1.48353F);
        part(root, "F41", 40, 52, -3, -1, 0, 3, 2, 2, -5.5F, -2, 1.2F, 0, 0, 1.48353F);
        part(root, "F51", 50, 52, -3, -1, 0, 3, 2, 2, -5.5F, -2, 3.4F, 0, 0, 1.48353F);
        part(root, "F11", 48, 38, 0, -1, -3, 2, 2, 3, -5.5F, -2, -3, 0, 0, 1.047198F);
        part(root, "F22", 20, 56, -3, -1, -1, 3, 2, 2, -5.6F, -4.5F, -2.2F, 0, 0, 1.134464F);
        part(root, "F32", 30, 56, -3, -1, -1, 3, 2, 2, -5.6F, -4.5F, 0, 0, 0, 1.134464F);
        part(root, "F42", 40, 56, -3, -1, -1, 3, 2, 2, -5.6F, -4.5F, 2.2F, 0, 0, 1.134464F);
        part(root, "F52", 50, 56, -3, -1, -1, 3, 2, 2, -5.6F, -4.5F, 4.4F, 0, 0, 1.134464F);
        part(root, "F12", 48, 34, -1, -1, -2, 2, 2, 2, -5, -1, -5.8F, 0, 0.3490659F, 1.047198F);
        part(root, "F23", 20, 60, -3, -1, -1, 3, 2, 2, -6.6F, -6.8F, -2.2F, 0, 0, 0.5235988F);
        part(root, "F33", 30, 60, -3, -1, -1, 3, 2, 2, -6.6F, -6.8F, 0, 0, 0, 0.5235988F);
        part(root, "F43", 40, 60, -3, -1, -1, 3, 2, 2, -6.6F, -6.8F, 2.2F, 0, 0, 0.5235988F);
        part(root, "F53", 50, 60, -3, -1, -1, 3, 2, 2, -6.6F, -6.8F, 4.4F, 0, 0, 0.5235988F);
        part(root, "F13", 48, 30, -1, -1, -2, 2, 2, 2, -5.5F, -1, -7.2F, 0, 1.047198F, 1.047198F);
    }

    private static void pointerParts(PartDefinition root) {
        part(root, "GBase", 24, 32, -2, -3, -4, 4, 3, 8, -6, 0, 1, 0, 0, 0.6108652F);
        part(root, "F31", 20, 52, -3, -1, 0, 3, 2, 2, -6, -2.8F, -1, 0, 0, -0.5235988F);
        part(root, "F21", 30, 52, -3, -1, -2, 3, 2, 2, -6, -2.8F, -1.2F, 0, 0, 0);
        part(root, "F41", 40, 52, -3, -1, 0, 3, 2, 2, -6, -2.8F, 1.2F, 0, 0, -0.5235988F);
        part(root, "F51", 50, 52, -3, -1, 0, 3, 2, 2, -6, -2.8F, 3.4F, 0, 0, -0.5235988F);
        part(root, "F11", 48, 38, -1, -1, -3, 2, 2, 3, -5, -1, -2.5F, 1.22173F, 1.745329F, -1.047198F);
        part(root, "F22", 20, 56, -3, -1, -1, 3, 2, 2, -8.5F, -3, -2.2F, 0, 0, 0);
        part(root, "F32", 30, 56, -3, -1, -1, 3, 2, 2, -8.5F, -2, 0, 0, 0, -1.919862F);
        part(root, "F42", 40, 56, -3, -1, -1, 3, 2, 2, -8.5F, -2, 2.2F, 0, 0, -1.919862F);
        part(root, "F52", 50, 56, -3, -1, -1, 3, 2, 2, -8.5F, -2, 4.4F, 0, 0, -1.919862F);
        part(root, "F12", 48, 34, -1, -1, -2, 2, 2, 2, -6, 0.5F, -4.5F, 1.22173F, 2.935045F, -1.047198F);
        part(root, "F23", 20, 60, -3, -1, -1, 3, 2, 2, -11, -2.8F, -2.2F, 0, 0, 0);
        part(root, "F33", 30, 60, -3, -1, -1, 3, 2, 2, -8, 0.5F, 0, 0, 0, -2.879793F);
        part(root, "F43", 40, 60, -3, -1, -1, 3, 2, 2, -8, 0.5F, 2.2F, 0, 0, -2.879793F);
        part(root, "F53", 50, 60, -3, -1, -1, 3, 2, 2, -8, 0.5F, 4.4F, 0, 0, -2.879793F);
        part(root, "F13", 48, 30, -1, -1, -2, 2, 2, 2, -7, 1, -4, 0.5235988F, 2.617994F, -1.047198F);
    }

    private static void part(PartDefinition root, String name, int u, int v,
                             float x, float y, float z, float width, float height, float depth,
                             float pivotX, float pivotY, float pivotZ,
                             float xRot, float yRot, float zRot) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, width, height, depth),
                PartPose.offsetAndRotation(pivotX, pivotY, pivotZ, xRot, yRot, zRot));
    }

    private static ModelLayerLocation layer(String path) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path), "main");
    }

    private enum Variant {
        FIST,
        CLAW,
        OPEN,
        POINTER
    }
}
