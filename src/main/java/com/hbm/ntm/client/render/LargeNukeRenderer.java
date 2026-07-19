package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.LargeNukeBlock;
import com.hbm.ntm.block.LargeNukeType;
import com.hbm.ntm.blockentity.LargeNukeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.EnumMap;
import java.util.Map;

public final class LargeNukeRenderer implements BlockEntityRenderer<LargeNukeBlockEntity> {
    public static final Map<LargeNukeType, ModelResourceLocation> MODELS = createModels();
    public static final ModelResourceLocation GADGET_WIRES = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/nuke_gadget_wires"));

    public LargeNukeRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(LargeNukeBlockEntity bomb, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        float facing = bomb.getBlockState().getValue(LargeNukeBlock.FACING).toYRot();
        switch (bomb.type()) {
            case GADGET -> poses.mulPose(Axis.YP.rotationDegrees(180.0F - facing));
            case BOY -> {
                poses.mulPose(Axis.YP.rotationDegrees(270.0F - facing));
                poses.translate(-2.0D, 0.0D, 0.0D);
            }
            case MIKE -> poses.mulPose(Axis.YP.rotationDegrees(180.0F - facing));
            case TSAR -> poses.mulPose(Axis.YP.rotationDegrees(270.0F - facing));
        }
        ThermalModelRenderer.render(MODELS.get(bomb.type()), poses, buffers, packedLight, packedOverlay);
        if (bomb.type() == LargeNukeType.GADGET
                && Minecraft.getInstance().options.graphicsMode().get() != GraphicsStatus.FAST) {
            ThermalModelRenderer.render(GADGET_WIRES, poses, buffers, packedLight, packedOverlay);
        }
        poses.popPose();
    }

    private static Map<LargeNukeType, ModelResourceLocation> createModels() {
        Map<LargeNukeType, ModelResourceLocation> models = new EnumMap<>(LargeNukeType.class);
        for (LargeNukeType type : LargeNukeType.values()) {
            models.put(type, ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                    HbmNtm.MOD_ID, "block/nuke_" + type.id() + "_body")));
        }
        return models;
    }

    @Override
    public AABB getRenderBoundingBox(LargeNukeBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX() - 5, pos.getY() - 2, pos.getZ() - 5,
                pos.getX() + 6, pos.getY() + 7, pos.getZ() + 6);
    }

    @Override public int getViewDistance() { return 256; }
}
