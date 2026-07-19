package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.PumpBlock;
import com.hbm.ntm.blockentity.PumpBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Four OBJ-group transforms shared by both pump textures. */
public final class PumpRenderer implements BlockEntityRenderer<PumpBlockEntity> {
    public static final ModelResourceLocation STEAM_BASE = model("pump_steam_base");
    public static final ModelResourceLocation STEAM_ROTOR = model("pump_steam_rotor");
    public static final ModelResourceLocation STEAM_ARMS = model("pump_steam_arms");
    public static final ModelResourceLocation STEAM_PISTON = model("pump_steam_piston");
    public static final ModelResourceLocation ELECTRIC_BASE = model("pump_electric_base");
    public static final ModelResourceLocation ELECTRIC_ROTOR = model("pump_electric_rotor");
    public static final ModelResourceLocation ELECTRIC_ARMS = model("pump_electric_arms");
    public static final ModelResourceLocation ELECTRIC_PISTON = model("pump_electric_piston");

    public PumpRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(PumpBlockEntity pump, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float rotation = Mth.lerp(partialTick, pump.lastRotor(), pump.rotor());
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F - pump.getBlockState()
                .getValue(PumpBlock.FACING).toYRot()));
        renderParts(pump.electric(), rotation, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    public static void renderParts(boolean electric, float rotation, PoseStack poses,
                                   MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ThermalModelRenderer.render(electric ? ELECTRIC_BASE : STEAM_BASE,
                poses, buffers, packedLight, packedOverlay);

        poses.pushPose();
        poses.translate(0.0D, 2.25D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees(rotation - 90.0F));
        poses.translate(0.0D, -2.25D, 0.0D);
        ThermalModelRenderer.render(electric ? ELECTRIC_ROTOR : STEAM_ROTOR,
                poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        double radians = rotation * Math.PI / 180.0D;
        double sin = Math.sin(radians) * 0.5D - 0.5D;
        double cos = Math.cos(radians) * 0.5D;
        double angle = Math.acos(cos / 2.0D) * 180.0D / Math.PI;
        double cath = Math.sqrt(1.0D + cos * cos / 2.0D);
        double lift = 1.0D - cath + sin;

        poses.pushPose();
        poses.translate(0.0D, lift, 0.0D);
        poses.translate(0.0D, 4.75D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) (90.0D - angle)));
        poses.translate(0.0D, -4.75D, 0.0D);
        ThermalModelRenderer.render(electric ? ELECTRIC_ARMS : STEAM_ARMS,
                poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, lift, 0.0D);
        ThermalModelRenderer.render(electric ? ELECTRIC_PISTON : STEAM_PISTON,
                poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    @Override public AABB getRenderBoundingBox(PumpBlockEntity pump) { return pump.renderBounds(); }
    @Override public int getViewDistance() { return 256; }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "block/" + path));
    }
}
