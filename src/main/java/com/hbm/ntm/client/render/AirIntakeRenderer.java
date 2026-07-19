package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.AirIntakeBlock;
import com.hbm.ntm.blockentity.AirIntakeBlockEntity;
import com.hbm.ntm.client.sound.AirIntakeSoundInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

/** Base/Fan split with a negative 45-degree-per-tick fan rotation. */
public final class AirIntakeRenderer implements BlockEntityRenderer<AirIntakeBlockEntity> {
    public static final ModelResourceLocation BASE = model("intake_base");
    public static final ModelResourceLocation FAN = model("intake_fan");
    private final Map<AirIntakeBlockEntity, AirIntakeSoundInstance> sounds = new WeakHashMap<>();

    public AirIntakeRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(AirIntakeBlockEntity intake, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        sounds.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (intake.active() && !sounds.containsKey(intake)) {
            AirIntakeSoundInstance sound = new AirIntakeSoundInstance(intake);
            sounds.put(intake, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }

        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(facingRotation(
                intake.getBlockState().getValue(AirIntakeBlock.FACING))));
        poses.translate(-0.5D, 0.0D, 0.5D);
        renderParts(intake.fan(partialTick), poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    public static void renderParts(float fan, PoseStack poses, MultiBufferSource buffers,
                                   int packedLight, int packedOverlay) {
        ThermalModelRenderer.render(BASE, poses, buffers, packedLight, packedOverlay);
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(-fan));
        ThermalModelRenderer.render(FAN, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    private static float facingRotation(Direction direction) {
        return switch (direction) {
            case EAST -> 0.0F;
            case NORTH -> 90.0F;
            case WEST -> 180.0F;
            case SOUTH -> 270.0F;
            default -> 0.0F;
        };
    }

    @Override public AABB getRenderBoundingBox(AirIntakeBlockEntity intake) { return intake.renderBounds(); }
    @Override public int getViewDistance() { return 256; }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "block/" + path));
    }
}
