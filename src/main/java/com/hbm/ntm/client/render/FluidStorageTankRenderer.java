package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.FluidStorageTankBlock;
import com.hbm.ntm.blockentity.FluidStorageTankBlockEntity;
import com.hbm.ntm.fluid.FluidTankProperties;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.EnumMap;
import java.util.Map;

public final class FluidStorageTankRenderer implements BlockEntityRenderer<FluidStorageTankBlockEntity> {
    public static final ModelResourceLocation FRAME = model("fluidtank_frame");
    public static final ModelResourceLocation DAMAGED_FRAME = model("fluidtank_damaged_frame");
    public static final ModelResourceLocation DAMAGED_INNER = model("fluidtank_damaged_inner");
    public static final Map<FluidIdentifierItem.Selection, ModelResourceLocation> TANKS =
            models("fluidtank_tank_");
    public static final Map<FluidIdentifierItem.Selection, ModelResourceLocation> DAMAGED_TANKS =
            models("fluidtank_damaged_tank_");

    public FluidStorageTankRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(FluidStorageTankBlockEntity tank, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(-tank.getBlockState()
                .getValue(FluidStorageTankBlock.FACING).toYRot()));
        renderParts(tank.selection(), tank.damaged(), poses, buffers, packedLight, packedOverlay);
        renderHazardDiamonds(tank.selection(), poses, buffers, packedLight);
        poses.popPose();
    }

    private static void renderHazardDiamonds(FluidIdentifierItem.Selection selection, PoseStack poses,
                                             MultiBufferSource buffers, int packedLight) {
        if (selection == null || selection == FluidIdentifierItem.Selection.NONE) return;
        FluidTankProperties.Profile profile = FluidTankProperties.get(selection);

        poses.pushPose();
        poses.translate(-.25D, .5D, -1.501D);
        poses.mulPose(Axis.YP.rotationDegrees(90F));
        poses.scale(1F, .375F, .375F);
        HazardDiamondRenderer.render(profile, poses, buffers, packedLight);
        poses.popPose();

        poses.pushPose();
        poses.translate(.25D, .5D, 1.501D);
        poses.mulPose(Axis.YP.rotationDegrees(-90F));
        poses.scale(1F, .375F, .375F);
        HazardDiamondRenderer.render(profile, poses, buffers, packedLight);
        poses.popPose();
    }

    public static void renderParts(FluidIdentifierItem.Selection selection, boolean damaged, PoseStack poses,
                                   MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (selection == null) selection = FluidIdentifierItem.Selection.NONE;
        if (damaged) {
            ThermalModelRenderer.render(DAMAGED_FRAME, poses, buffers, packedLight, packedOverlay);
            ThermalModelRenderer.render(DAMAGED_INNER, poses, buffers, packedLight, packedOverlay);
            ThermalModelRenderer.render(DAMAGED_TANKS.get(selection), poses, buffers, packedLight, packedOverlay);
        } else {
            ThermalModelRenderer.render(FRAME, poses, buffers, packedLight, packedOverlay);
            ThermalModelRenderer.render(TANKS.get(selection), poses, buffers, packedLight, packedOverlay);
        }
    }

    private static Map<FluidIdentifierItem.Selection, ModelResourceLocation> models(String prefix) {
        EnumMap<FluidIdentifierItem.Selection, ModelResourceLocation> models =
                new EnumMap<>(FluidIdentifierItem.Selection.class);
        for (FluidIdentifierItem.Selection selection : FluidIdentifierItem.Selection.values()) {
            models.put(selection, model(prefix + selection.id()));
        }
        return models;
    }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/" + path));
    }

    @Override public AABB getRenderBoundingBox(FluidStorageTankBlockEntity tank) {
        BlockPos pos = tank.getBlockPos();
        return new AABB(pos.getX() - 6D, pos.getY(), pos.getZ() - 6D,
                pos.getX() + 7D, pos.getY() + 4D, pos.getZ() + 7D);
    }

    @Override public int getViewDistance() { return 256; }
}
