package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.IndustrialTurbineBlock;
import com.hbm.ntm.blockentity.IndustrialTurbineBlockEntity;
import com.hbm.ntm.client.sound.IndustrialTurbineSoundInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

/** Turbine, gauge and flywheel, each spinning around its own sacred pivot. */
public final class IndustrialTurbineRenderer implements BlockEntityRenderer<IndustrialTurbineBlockEntity> {
    public static final ModelResourceLocation BASE = model("industrial_turbine_base");
    public static final ModelResourceLocation GAUGE = model("industrial_turbine_gauge");
    public static final ModelResourceLocation FLYWHEEL = model("industrial_turbine_flywheel");
    private final Map<IndustrialTurbineBlockEntity, IndustrialTurbineSoundInstance> sounds = new WeakHashMap<>();

    public IndustrialTurbineRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(IndustrialTurbineBlockEntity turbine, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        sounds.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (turbine.spin() > 0D && !sounds.containsKey(turbine)) {
            IndustrialTurbineSoundInstance sound = new IndustrialTurbineSoundInstance(turbine);
            sounds.put(turbine, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }

        float rotor = Mth.lerp(partialTick, turbine.lastRotor(), turbine.rotor());
        poses.pushPose();
        poses.translate(0.5D, 0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(facingRotation(
                turbine.getBlockState().getValue(IndustrialTurbineBlock.FACING))));
        renderParts(turbine.grade().ordinal(), rotor, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    public static void renderParts(int grade, float rotor, PoseStack poses, MultiBufferSource buffers,
                                   int packedLight, int packedOverlay) {
        ThermalModelRenderer.render(BASE, poses, buffers, packedLight, packedOverlay);

        poses.pushPose();
        poses.translate(0D, 1.5D, 0D);
        poses.mulPose(Axis.ZP.rotationDegrees(135F - grade * 90F));
        poses.translate(0D, -1.5D, 0D);
        ThermalModelRenderer.render(GAUGE, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0D, 1.5D, 0D);
        poses.mulPose(Axis.ZP.rotationDegrees(-rotor));
        poses.translate(0D, -1.5D, 0D);
        ThermalModelRenderer.render(FLYWHEEL, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    private static float facingRotation(Direction direction) {
        return switch (direction) {
            case NORTH -> 180F;
            case EAST -> 90F;
            case SOUTH -> 0F;
            case WEST -> 270F;
            default -> 0F;
        };
    }

    @Override public AABB getRenderBoundingBox(IndustrialTurbineBlockEntity turbine) {
        return turbine.renderBounds();
    }
    @Override public int getViewDistance() { return 256; }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "block/" + path));
    }
}
