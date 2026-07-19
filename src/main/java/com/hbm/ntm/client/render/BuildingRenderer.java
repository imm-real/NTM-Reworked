package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.entity.BuildingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Unculled boxcar building branch at its deeply specific OBJ scale. */
public final class BuildingRenderer extends EntityRenderer<BuildingEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "models/weapons/building.obj");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/models/weapons/building.png");

    private EnvsuitMesh mesh;

    public BuildingRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(BuildingEntity building, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        mesh().render("Cube", poses.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                1.0F, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, -1);
        poses.popPose();
        super.render(building, yaw, partialTick, poses, buffers, packedLight);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) {
            mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(), MODEL,
                    Set.of("Cube"), "Subtlety falling building");
        }
        return mesh;
    }

    @Override
    public ResourceLocation getTextureLocation(BuildingEntity entity) {
        return TEXTURE;
    }
}
