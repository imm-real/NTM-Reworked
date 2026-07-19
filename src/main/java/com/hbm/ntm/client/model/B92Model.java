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

/** The B92, lovingly constructed from all available cubes. */
public final class B92Model {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "b92"), "main");
    private final ModelPart root;
    private final ModelPart pump1;
    private final ModelPart pump2;

    public B92Model(ModelPart root) {
        this.root = root;
        this.pump1 = root.getChild("Pump1");
        this.pump2 = root.getChild("Pump2");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "Muzzle1", 22, 36, 0,0,0, 2,3,2, -24,0.5F,-1, 0,0,0);
        part(root, "Barrel1", 0, 0, 0,0,0, 24,2,3, -24,1,-1.5F, 0,0,0);
        part(root, "Barrel2", 0, 5, 0,0,0, 22,1,2, -22,0.5F,-1, 0,0,0);
        part(root, "Grip", 0, 8, 0,0,0, 20,3,4, -20,3,-2, 0,0,0);
        part(root, "Front1", 10, 36, 0,0,0, 2,4,4, -22,0.5F,-2, 0,0,0);
        part(root, "Front2", 0, 36, 0,0,0, 2,6,3, -22,0,-1.5F, 0,0,0);
        part(root, "Body", 0, 15, 0,0,0, 15,7,4, 0,0.5F,-2, 0,0,0);
        part(root, "Top", 28, 60, 0,0,0, 15,1,3, 0,0,-1.5F, 0,0,0);
        part(root, "GripBottom", 24, 43, 0,0,0, 18,1,2, -18,5.5F,-1, 0,0,0);
        part(root, "Handle", 0, 45, 0,0,0, 6,15,4, 6,7,-2, 0,0,-0.2268928F);
        part(root, "HandleBack", 20, 46, 5.5F,0,0, 1,15,3, 6,7,-1.5F, 0,0,-0.2268928F);
        part(root, "Frame1", 28, 57, 0,0,0, 7,1,2, 0.5F,11,-1, 0,0,0);
        part(root, "Frame2", 28, 51, 0,0,0, 2,4,2, -2,6.5F,-1, 0,0,0);
        part(root, "Frame3", 46, 57, 0,-1,0, 3,1,2, -2,10.5F,-1, 0,0,0.5235988F);
        part(root, "Trigger", 36, 53, 0,0,0, 2,3,1, 4,7,-0.5F, 0,0,0.1919862F);
        part(root, "BackPlate1", 56, 53, -1,0,0, 1,4,3, 15,0,-1.5F, 0,0,-0.5235988F);
        part(root, "Back", 42, 49, 0,0,0, 2,4,4, 15,3.5F,-2, 0,0,0);
        part(root, "BackPlate2", 48, 5, -2,0,0, 2,4,4, 15,0.5F,-2, 0,0,-0.4886922F);
        part(root, "Pump1", 46, 29, 0,0,0, 7,2,2, 10,1,-1, 0,0,0);
        part(root, "Pump2", 44, 33, 0,0,0, 3,3,7, 17,0.5F,-3.5F, 0,0,0);
        part(root, "BodyPlate", 0, 26, 0,0,0, 14,5,5, 1.5F,2,-2.5F, 0,0,0);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void part(PartDefinition root, String name, int u, int v,
                             float x, float y, float z, float width, float height, float depth,
                             float pivotX, float pivotY, float pivotZ,
                             float xRot, float yRot, float zRot) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).mirror().addBox(x, y, z, width, height, depth),
                PartPose.offsetAndRotation(pivotX, pivotY, pivotZ, xRot, yRot, zRot));
    }

    public void render(PoseStack poses, VertexConsumer consumer, int light, int overlay, float pumpTranslation) {
        float pump1X = pump1.x;
        float pump2X = pump2.x;
        // ModelRenderer.offsetX was a direct render-space translation. ModelPart.x is
        // stored in pixels and divided by sixteen while rendering.
        pump1.x += pumpTranslation * 16.0F;
        pump2.x += pumpTranslation * 16.0F;
        root.render(poses, consumer, light, overlay);
        pump1.x = pump1X;
        pump2.x = pump2X;
    }
}
