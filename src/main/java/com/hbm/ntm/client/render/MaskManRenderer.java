package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.entity.MaskManEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

public final class MaskManRenderer extends EntityRenderer<MaskManEntity> {
    private static final ResourceLocation MODEL = id("models/mobs/maskman.obj");
    private static final ResourceLocation TEXTURE = id("textures/entity/maskman.png");
    private static final ResourceLocation IOU = id("textures/entity/iou.png");
    private static final Set<String> GROUPS = Set.of(
            "Torso", "LLeg", "RLeg", "LArm", "RArm", "Head", "Skull", "IOU");

    private EnvsuitMesh mesh;

    public MaskManRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.0F;
    }

    @Override
    public void render(MaskManEntity maskMan, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        float bodyYaw = Mth.rotLerp(partialTick, maskMan.yBodyRotO, maskMan.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, maskMan.yHeadRotO, maskMan.yHeadRot) - bodyYaw;
        float walk = maskMan.walkAnimation.position(partialTick);
        float speed = maskMan.walkAnimation.speed(partialTick) * 0.5F;
        double swing = Math.toDegrees(Mth.cos(walk / 2.0F + Mth.PI) * 1.4F * speed);
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poses.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poses.mulPose(Axis.XP.rotationDegrees((float) (swing * -0.1D)));

        VertexConsumer body = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        render("Torso", poses, body, packedLight);

        poses.pushPose();
        poses.translate(-0.5D, 1.75D, -0.5D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) swing));
        render("LLeg", poses, body, packedLight);
        poses.popPose();

        poses.pushPose();
        poses.translate(-0.5D, 1.75D, 0.5D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) -swing));
        render("RLeg", poses, body, packedLight);
        poses.popPose();

        poses.pushPose();
        poses.translate(-0.5D, 3.75D, -1.5D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) (swing * 0.25D)));
        render("LArm", poses, body, packedLight);
        poses.popPose();

        poses.pushPose();
        poses.translate(-0.5D, 3.75D, 1.5D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) (swing * -0.25D)));
        render("RArm", poses, body, packedLight);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.5D, 4.0D, 0.0D);
        poses.mulPose(Axis.YP.rotationDegrees(-headYaw));
        if (maskMan.getHealth() >= maskMan.getMaxHealth() * 0.5F) {
            render("Head", poses, body, packedLight);
        } else {
            render("Skull", poses, body, packedLight);
            VertexConsumer note = buffers.getBuffer(RenderType.entityCutout(IOU));
            render("IOU", poses, note, packedLight);
        }
        poses.popPose();
        poses.popPose();
        super.render(maskMan, yaw, partialTick, poses, buffers, packedLight);
    }

    private void render(String group, PoseStack poses, VertexConsumer consumer, int packedLight) {
        mesh().render(group, poses.last(), consumer, 1.0F, packedLight,
                OverlayTexture.NO_OVERLAY, -1);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) {
            mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(),
                    MODEL, GROUPS, "Mask Man");
        }
        return mesh;
    }

    @Override
    public ResourceLocation getTextureLocation(MaskManEntity entity) {
        return TEXTURE;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }
}
