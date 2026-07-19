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

/** Direct cube-for-cube conversion of the removed 128x64 ModelB93. */
public final class B93Model {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "b93"), "main");
    private final ModelPart solid;
    private final ModelPart glass;
    private final ModelPart pump1;
    private final ModelPart pump2;

    public B93Model(ModelPart root) {
        solid = root.getChild("solid");
        glass = root.getChild("glass");
        pump1 = solid.getChild("Pump1");
        pump2 = solid.getChild("Pump2");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition solid = root.addOrReplaceChild("solid", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition glass = root.addOrReplaceChild("glass", CubeListBuilder.create(), PartPose.ZERO);

        part(solid,"Muzzle1",22,36,0,0,0,2,3,2,-24,0.5F,-1,0,0,0);
        part(solid,"Barrel1",0,0,0,0,0,24,2,3,-24,1,-1.5F,0,0,0);
        part(solid,"Barrel2",0,5,0,0,0,22,1,2,-22,0.5F,-1,0,0,0);
        part(solid,"Grip",0,8,0,0,0,20,3,4,-20,3,-2,0,0,0);
        part(solid,"Front1",10,36,0,0,0,2,4,4,-22,0.5F,-2,0,0,0);
        part(solid,"Front2",0,36,0,0,0,2,6,3,-22,0,-1.5F,0,0,0);
        part(solid,"Body",0,15,0,0,0,15,7,4,0,0.5F,-2,0,0,0);
        part(solid,"Top",28,60,0,0,0,15,1,3,0,0,-1.5F,0,0,0);
        part(solid,"GripBottom",24,43,0,0,0,18,1,2,-18,5.5F,-1,0,0,0);
        part(solid,"Handle",0,45,0,0,0,6,15,4,6,7,-2,0,0,-0.2268928F);
        part(solid,"HandleBack",20,46,5.5F,0,0,1,15,3,6,7,-1.5F,0,0,-0.2268928F);
        part(solid,"Frame1",28,57,0,0,0,7,1,2,0.5F,11,-1,0,0,0);
        part(solid,"Frame2",28,51,0,0,0,2,4,2,-2,6.5F,-1,0,0,0);
        part(solid,"Frame3",46,57,0,-1,0,3,1,2,-2,10.5F,-1,0,0,0.5235988F);
        part(solid,"Trigger",36,53,0,0,0,2,3,1,4,7,-0.5F,0,0,0.1919862F);
        part(solid,"BackPlate1",56,53,-1,0,0,1,4,3,15,0,-1.5F,0,0,-0.5235988F);
        part(solid,"Back",42,49,0,0,0,2,4,4,15,3.5F,-2,0,0,0);
        part(solid,"BackPlate2",48,5,-2,0,0,2,4,4,15,0.5F,-2,0,0,-0.4886922F);
        part(solid,"Pump1",46,29,0,0,0,7,2,2,10,1,-1,0,0,0);
        part(solid,"Pump2",44,33,0,0,0,3,3,7,17,0.5F,-3.5F,0,0,0);
        part(solid,"BodyPlate",0,26,0,0,0,14,5,5,1.5F,2,-2.5F,0,0,0);
        part(solid,"Muz1",90,3,0,0,0,2,5,3,-26,-0.5F,-1.5F,0,0,0);
        part(solid,"Muz2",64,2,0,0,0,2,3,5,-26,0.5F,-2.5F,0,0,0);
        part(solid,"Muz3",78,3,0,0,0,2,4,4,-26,0,-2,0,0,0);
        part(solid,"Damp1",64,53,0,0,0,24,7,4,-50,-1.5F,-2,0,0,0);
        part(solid,"Damp2",64,42,0,0,0,24,4,7,-50,0,-3.5F,0,0,0);
        part(solid,"Damp3",64,30,0,0,0,24,6,6,-50,-1,-3,0,0,0);
        part(solid,"DampFront",64,22,0,0,0,2,4,4,-51.1F,0,-2,0,0,0);
        part(solid,"EmitterRod",64,18,0,0,0,4,2,2,-55,1,-1,0,0,0);
        part(solid,"EmitterCrystal",76,24,0,-1.5F,-1.5F,3,3,3,-57,2,0,0.7853982F,0,0);
        part(solid,"EmitterClamp1",88,27,-5,0,0,5,2,1,-50,1,-0.5F,0,0,1.047198F);
        part(solid,"EmitterClamp2",88,24,-5,-2,0,5,2,1,-50,3,-0.5F,0,0,-1.047198F);
        part(solid,"EmitterClamp3",100,27,-5,0,0,5,1,2,-50,1.5F,-1,0,-1.047198F,0);
        part(solid,"EmitterClamp4",100,24,-5,0,-2,5,1,2,-50,1.5F,1,0,1.047198F,0);
        part(solid,"EmitterClamp5",76,22,-6,0,0,7,1,1,-53,-4,-0.5F,0,0,-0.5235988F);
        part(solid,"EmitterClamp6",76,20,-6,-1,0,7,1,1,-53,8,-0.5F,0,0,0.5235988F);
        part(solid,"EmitterClamp7",92,22,-6,0,0,7,1,1,-53,1.5F,-6,0,0.5235988F,0);
        part(solid,"EmitterClamp8",92,20,-6,0,-1,7,1,1,-53,1.5F,6,0,-0.5235988F,0);
        part(solid,"PowBox",76,11,0,0,0,10,5,4,4,2,2.5F,0,0,0);
        part(solid,"PowPanel",44,24,0,0,0,9,4,1,4.5F,2.5F,6,0,0,0);
        part(glass,"Nix1",56,17,0,0,0,2,5,2,11,-3,4,0,0,0);
        part(glass,"Nix2",48,17,0,0,0,2,5,2,8,-3,4,0,0,0);
        part(glass,"Nix3",40,17,0,0,0,2,5,2,5,-3,4,0,0,0);
        part(solid,"Nix11",72,15,0,0,0,1,2,1,11.5F,-5,4.5F,0,0,0);
        part(solid,"Nix21",68,15,0,0,0,1,2,1,8.5F,-5,4.5F,0,0,0);
        part(solid,"Nix31",64,15,0,0,0,1,2,1,5.5F,-5,4.5F,0,0,0);
        part(solid,"Nix12",72,10,0,0,0,1,4,1,11.5F,-1.5F,4.5F,0,0,0);
        part(solid,"Nix22",68,10,0,0,0,1,4,1,8.5F,-1.5F,4.5F,0,0,0);
        part(solid,"Nix32",64,10,0,0,0,1,4,1,5.5F,-1.5F,4.5F,0,0,0);
        part(solid,"Pylon",114,24,0,0,0,2,2,4,-35,1,3.5F,0,0,0);
        part(solid,"Wire1",82,0,0,0,0,20,0,3,-24,7,4,0,0,0);
        part(solid,"Wire2",104,3,0,0,0,9,0,3,-4,7,4,0,0,-0.4363323F);
        part(solid,"Wire3",100,6,-11,0,0,11,0,3,-24,7,4,0,0,0.4363323F);
        part(solid,"PowPylon",108,17,0,0,0,2,2,5,2.5F,3,2.5F,0,0,0);
        return LayerDefinition.create(mesh, 128, 64);
    }

    private static void part(PartDefinition root, String name, int u, int v,
                             float x, float y, float z, float width, float height, float depth,
                             float pivotX, float pivotY, float pivotZ,
                             float xRot, float yRot, float zRot) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).mirror().addBox(x, y, z, width, height, depth),
                PartPose.offsetAndRotation(pivotX, pivotY, pivotZ, xRot, yRot, zRot));
    }

    public void renderSolid(PoseStack poses, VertexConsumer consumer, int light, int overlay,
                            float pumpTranslation) {
        float pump1X = pump1.x;
        float pump2X = pump2.x;
        pump1.x += pumpTranslation * 16.0F;
        pump2.x += pumpTranslation * 16.0F;
        solid.render(poses, consumer, light, overlay);
        pump1.x = pump1X;
        pump2.x = pump2X;
    }

    public void renderGlass(PoseStack poses, VertexConsumer consumer, int light, int overlay) {
        glass.render(poses, consumer, light, overlay);
    }
}
