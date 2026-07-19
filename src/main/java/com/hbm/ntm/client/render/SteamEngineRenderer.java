package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.SteamEngineBlock;
import com.hbm.ntm.blockentity.SteamEngineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Source group transforms for the flywheel, shaft, linkage and piston. */
public final class SteamEngineRenderer implements BlockEntityRenderer<SteamEngineBlockEntity> {
    public static final ModelResourceLocation BASE = model("steam_engine_base");
    public static final ModelResourceLocation FLYWHEEL = model("steam_engine_flywheel");
    public static final ModelResourceLocation SHAFT = model("steam_engine_shaft");
    public static final ModelResourceLocation TRANSMISSION = model("steam_engine_transmission");
    public static final ModelResourceLocation PISTON = model("steam_engine_piston");

    public SteamEngineRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(SteamEngineBlockEntity engine, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float rotation = Mth.lerp(partialTick, engine.lastRotor(), engine.rotor());
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F - engine.getBlockState()
                .getValue(SteamEngineBlock.FACING).toYRot()));
        poses.translate(2.0D, 0.0D, 0.0D);
        renderParts(rotation, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    public static void renderParts(float rotation, PoseStack poses, MultiBufferSource buffers,
                                   int packedLight, int packedOverlay) {
        ThermalModelRenderer.render(BASE, poses, buffers, packedLight, packedOverlay);

        poses.pushPose();
        poses.translate(2.0D, 1.375D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees(-rotation));
        poses.translate(-2.0D, -1.375D, 0.0D);
        ThermalModelRenderer.render(FLYWHEEL, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, 1.375D, -0.5D);
        poses.mulPose(Axis.XP.rotationDegrees(rotation * 2.0F));
        poses.translate(0.0D, -1.375D, 0.5D);
        ThermalModelRenderer.render(SHAFT, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        double radians = rotation * Math.PI / 180.0D;
        double sin = Math.sin(radians) * 0.25D - 0.25D;
        double cos = Math.cos(radians) * 0.25D;
        double angle = Math.acos(cos / 1.875D) * 180.0D / Math.PI;
        poses.pushPose();
        poses.translate(sin, cos, 0.0D);
        poses.translate(2.25D, 1.375D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) (90.0D - angle)));
        poses.translate(-2.25D, -1.375D, 0.0D);
        ThermalModelRenderer.render(TRANSMISSION, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        double cath = Math.sqrt(3.515625D - cos * cos / 2.0D);
        poses.pushPose();
        poses.translate(1.875D - cath + sin, 0.0D, 0.0D);
        ThermalModelRenderer.render(PISTON, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    @Override public AABB getRenderBoundingBox(SteamEngineBlockEntity engine) { return engine.renderBounds(); }
    @Override public int getViewDistance() { return 256; }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "block/" + path));
    }
}
