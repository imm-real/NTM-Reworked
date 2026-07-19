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

/** Direct conversion of ModelRubble, including its deliberately overlapping ten chunks. */
public final class PowerFistRubbleModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "power_fist_rubble"), "main");

    private final ModelPart root;

    public PowerFistRubbleModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "Shape1", 0, 0, 0, 0, 0, 14, 6, 6, -7, 1, 2, 0, 0, 0);
        part(root, "Shape2", 0, 0, 0, 0, 0, 6, 13, 5, -7, -6, -5, 0, 0, 0);
        part(root, "Shape3", 0, 0, 0, 0, 0, 6, 6, 6, 1, 1, -5, 0, 0, 0);
        part(root, "Shape4", 0, 0, 0, 0, 0, 14, 7, 4, -7, -7, 2, 0, 0.4363323F, 0);
        part(root, "Shape5", 0, 0, 0, 0, 0, 6, 6, 11, 0, -6, -5, 0, 0, 0);
        part(root, "Shape6", 0, 0, 0, 0, 0, 8, 8, 8, -4, -4, -4, 0, 0, 0);
        part(root, "Shape7", 0, 0, 0, 0, 0, 6, 5, 7, -7, -5, 1, 0, 0, 0);
        part(root, "Shape8", 0, 0, 0, 0, 0, 12, 6, 4, -6, -1, 3, 0, 0, -0.3490659F);
        part(root, "Shape9", 0, 0, 0, 0, 0, 12, 6, 6, -6, 2, -3, 0, -0.2094395F, 0);
        part(root, "Shape10", 0, 0, 0, 0, 0, 6, 10, 4, -5, -3, -6, 0, 0, -0.3490659F);
        // ModelRubble called setTextureSize(64, 32) after addBox. In 1.7.10 the
        // ModelBox had already captured the model-level 16x16 dimensions, so the
        // The oversized repeating UVs are ugly on purpose and visible at runtime.
        return LayerDefinition.create(mesh, 16, 16);
    }

    public void render(PoseStack poses, VertexConsumer consumer, int light, int overlay) {
        root.render(poses, consumer, light, overlay);
    }

    private static void part(PartDefinition root, String name, int u, int v,
                             float x, float y, float z, float width, float height, float depth,
                             float pivotX, float pivotY, float pivotZ,
                             float xRot, float yRot, float zRot) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, width, height, depth),
                PartPose.offsetAndRotation(pivotX, pivotY, pivotZ, xRot, yRot, zRot));
    }
}
