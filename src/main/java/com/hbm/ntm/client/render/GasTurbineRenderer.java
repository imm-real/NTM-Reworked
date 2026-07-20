package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.GasTurbineBlock;
import com.hbm.ntm.blockentity.GasTurbineBlockEntity;
import com.hbm.ntm.client.sound.GasTurbineSoundInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

public final class GasTurbineRenderer implements BlockEntityRenderer<GasTurbineBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/turbinegas_obj"));
    private final Map<GasTurbineBlockEntity, GasTurbineSoundInstance> sounds = new WeakHashMap<>();

    public GasTurbineRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(GasTurbineBlockEntity turbine, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        sounds.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (turbine.rpm() >= 10 && turbine.state() != -1 && !sounds.containsKey(turbine)) {
            GasTurbineSoundInstance sound = new GasTurbineSoundInstance(turbine);
            sounds.put(turbine, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }

        poses.pushPose();
        poses.translate(0.5D, 0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(facingRotation(
                turbine.getBlockState().getValue(GasTurbineBlock.FACING))));
        renderModel(poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    public static void renderModel(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        ThermalModelRenderer.render(MODEL, poses, buffers, light, overlay);
    }

    private static float facingRotation(Direction direction) {
        return switch (direction) {
            case EAST -> 0F;
            case NORTH -> 90F;
            case WEST -> 180F;
            case SOUTH -> 270F;
            default -> 0F;
        };
    }

    @Override public AABB getRenderBoundingBox(GasTurbineBlockEntity turbine) {
        BlockPos core = turbine.getBlockPos();
        Direction facing = turbine.getBlockState().getValue(GasTurbineBlock.FACING);
        AABB bounds = new AABB(core);
        for (BlockPos part : GasTurbineBlock.partPositions(core, facing)) bounds = bounds.minmax(new AABB(part));
        return bounds.inflate(0.25D);
    }

    @Override public int getViewDistance() { return 256; }
}
