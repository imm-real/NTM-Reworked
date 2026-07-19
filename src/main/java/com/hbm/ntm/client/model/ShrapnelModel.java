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

/** Direct conversion of {@code render.model.ModelShrapnel}: a single 4x4x4 mirrored cube on a 16x8 sheet. */
public final class ShrapnelModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "shrapnel"), "main");

    private final ModelPart root;

    public ShrapnelModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("bullet",
                CubeListBuilder.create().texOffs(0, 0).mirror().addBox(0.0F, 0.0F, 0.0F, 4, 4, 4),
                PartPose.offset(1.0F, -0.5F, -0.5F));
        return LayerDefinition.create(mesh, 16, 8);
    }

    public void render(PoseStack poses, VertexConsumer consumer, int light, int overlay) {
        root.render(poses, consumer, light, overlay);
    }
}
