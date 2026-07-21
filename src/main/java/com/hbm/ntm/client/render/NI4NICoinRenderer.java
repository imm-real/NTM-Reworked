package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.entity.NI4NICoinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Set;

public final class NI4NICoinRenderer extends EntityRenderer<NI4NICoinEntity> {
    private static final ResourceLocation MODEL = id("models/trinkets/chip.obj");
    private static final ResourceLocation TEXTURE = id("textures/models/trinkets/chip_gold.png");
    private EnvsuitMesh mesh;

    public NI4NICoinRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(NI4NICoinEntity coin, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.mulPose(Axis.YN.rotationDegrees(Mth.rotLerp(partialTick, coin.yRotO, coin.getYRot()) - 90.0F));
        poses.mulPose(Axis.ZP.rotationDegrees((coin.tickCount + partialTick) * 45.0F));
        mesh().render("Cylinder", poses.last(), buffers.getBuffer(RenderType.entityCutout(TEXTURE)),
                0.125F, packedLight, OverlayTexture.NO_OVERLAY, -1);
        poses.popPose();
        super.render(coin, yaw, partialTick, poses, buffers, packedLight);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) {
            mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(), MODEL,
                    Set.of("Cylinder"), "N I 4 N I coin");
        }
        return mesh;
    }

    @Override public ResourceLocation getTextureLocation(NI4NICoinEntity entity) { return TEXTURE; }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }
}
