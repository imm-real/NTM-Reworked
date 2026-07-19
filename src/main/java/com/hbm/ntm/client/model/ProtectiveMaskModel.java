package com.hbm.ntm.client.model;

import com.hbm.ntm.HbmNtm;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Goggles, gas mask and M65 assembled from vintage Techne cubes. */
public final class ProtectiveMaskModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation GOGGLES_LAYER = layer("goggles");
    public static final ModelLayerLocation GAS_MASK_LAYER = layer("gas_mask");
    public static final ModelLayerLocation M65_LAYER = layer("gas_mask_m65");

    private final ModelPart filter;

    public ProtectiveMaskModel(ModelPart root, Kind kind) {
        super(root);
        ModelPart gear = head.getChild("gear");
        if (kind == Kind.GAS_MASK) {
            gear.xScale = 1.15F;
            gear.yScale = 1.15F;
            gear.zScale = 1.15F;
        } else if (kind == Kind.M65) {
            float scale = 18.0F / 16.0F * 1.01F;
            gear.xScale = scale;
            gear.yScale = scale;
            gear.zScale = scale;
        }
        filter = gear.hasChild("filter") ? gear.getChild("filter") : null;
    }

    public void setFilterVisible(boolean visible) {
        if (filter != null) filter.visible = visible;
    }

    public static LayerDefinition createGogglesLayer() {
        MeshDefinition mesh = emptyHumanoidMesh();
        PartDefinition gear = gear(mesh);
        part(gear, "band_front", 0, 0, 0, 0, 0, 9, 3, 1, -4.5F, -5.0F, -4.5F, 0, 0, 0);
        part(gear, "band_wrap", 0, 4, 0, 0, 0, 9, 2, 5, -4.5F, -5.0F, -3.5F, 0, 0, 0);
        part(gear, "lens_right", 26, 0, 0, 0, 0, 2, 2, 1, 1.0F, -4.5F, -5.0F, 0, 0, 0);
        part(gear, "lens_left", 20, 0, 0, 0, 0, 2, 2, 1, -3.0F, -4.5F, -5.0F, 0, 0, 0);
        part(gear, "band_back", 0, 11, 0, 0, 0, 9, 1, 4, -4.5F, -5.0F, 0.5F, 0, 0, 0);
        return LayerDefinition.create(mesh, 64, 32);
    }

    public static LayerDefinition createGasMaskLayer() {
        MeshDefinition mesh = emptyHumanoidMesh();
        PartDefinition gear = gear(mesh);
        part(gear, "hood", 0, 0, 0, 0, 0, 8, 8, 3, -4.0F, -7.9625F, -4.0F, 0, 0, 0);
        part(gear, "lens_left", 22, 0, 0, 0, 0, 2, 2, 1, -3.0F, -4.9625F, -4.5333333F, 0, 0, 0);
        part(gear, "lens_right", 22, 0, 0, 0, 0, 2, 2, 1, 1.0F, -4.9625F, -4.5F, 0, 0, 0);
        part(gear, "outlet", 0, 11, 0, 0, 0, 2, 2, 2, -1.0F, -2.9625F, -4.0F,
                -0.7853982F, 0, 0);
        part(gear, "filter", 0, 15, 0, 2, -0.5F, 3, 4, 3, -1.5F, -2.9625F, -4.0F,
                -0.7853982F, 0, 0);
        part(gear, "strap", 0, 22, 0, 0, 0, 8, 1, 5, -4.0F, -4.9625F, -1.0F, 0, 0, 0);
        return LayerDefinition.create(mesh, 64, 32);
    }

    public static LayerDefinition createM65Layer() {
        MeshDefinition mesh = emptyHumanoidMesh();
        PartDefinition gear = gear(mesh);
        part(gear, "hood", 0, 0, 0, 0, 0, 8, 8, 8, -4.0F, -7.5F, -4.0F, 0, 0, 0);
        part(gear, "nose", 0, 16, 0, 0, 0, 3, 3, 1, -1.5F, -3.0F, -5.0F, 0, 0, 0);
        part(gear, "outlet", 0, 20, 0, -2, 0, 2, 2, 1, -1.0F, -3.0F, -5.0F,
                -0.4799655F, 0, 0);
        part(gear, "nose_slope", 8, 16, 0, 0, -2, 3, 2, 2, -1.5F, -1.5F, -4.0F,
                0.6108652F, 0, 0);
        part(gear, "lens_left", 0, 23, 0, 0, 0, 3, 3, 0, -3.5F, -5.5F, -4.2F, 0, 0, 0);
        part(gear, "lens_right", 0, 26, 0, 0, 0, 3, 3, 0, 0.5F, -5.5F, -4.2F, 0, 0, 0);
        part(gear, "valve", 6, 20, 0, 0, 0, 2, 2, 1, -1.0F, -2.7F, -6.0F, 0, 0, 0);

        PartDefinition filter = gear.addOrReplaceChild("filter", CubeListBuilder.create(), PartPose.ZERO);
        part(filter, "connector", 6, 23, 0, 0, -3, 2, 2, 1, -1.0F, -1.5F, -4.0F,
                0.6108652F, 0, 0);
        part(filter, "canister_inner", 18, 21, 0, -1, -5, 3, 4, 2, -1.5F, -1.5F, -4.0F,
                0.6108652F, 0, 0);
        part(filter, "canister_outer", 18, 16, 0, -0.5F, -5, 4, 3, 2, -2.0F, -1.5F, -4.0F,
                0.6108652F, 0, 0);
        return LayerDefinition.create(mesh, 32, 32);
    }

    private static MeshDefinition emptyHumanoidMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);
        return mesh;
    }

    private static PartDefinition gear(MeshDefinition mesh) {
        return mesh.getRoot().getChild("head").addOrReplaceChild("gear", CubeListBuilder.create(), PartPose.ZERO);
    }

    private static void part(PartDefinition parent, String name, int u, int v,
                             float x, float y, float z, float width, float height, float depth,
                             float pivotX, float pivotY, float pivotZ,
                             float xRot, float yRot, float zRot) {
        parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).mirror().addBox(x, y, z, width, height, depth),
                PartPose.offsetAndRotation(pivotX, pivotY, pivotZ, xRot, yRot, zRot));
    }

    private static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id), "main");
    }

    public enum Kind {
        GOGGLES,
        GAS_MASK,
        M65
    }
}
