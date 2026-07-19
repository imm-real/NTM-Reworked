package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.TurbofanBlock;
import com.hbm.ntm.blockentity.TurbofanBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Turbofan body, blades and afterburner, all facing the expensive direction. */
public final class TurbofanRenderer implements BlockEntityRenderer<TurbofanBlockEntity> {
    public static final ModelResourceLocation BODY = model("turbofan_body");
    public static final ModelResourceLocation BLADES = model("turbofan_blades");
    public static final ModelResourceLocation AFTERBURNER_BACK = model("turbofan_afterburner_back");
    public static final ModelResourceLocation AFTERBURNER_HOT = model("turbofan_afterburner_hot");

    public TurbofanRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TurbofanBlockEntity turbofan, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // Sable may render a moving plot before its normal client BE ticker for this game tick.
        // The block entity guard makes this a no-op when the ordinary ticker already advanced it.
        turbofan.ensureClientAnimationTick();
        float spin = Mth.lerp(partialTick, turbofan.lastSpin(), turbofan.spin());
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(facingRotation(
                turbofan.getBlockState().getValue(TurbofanBlock.FACING))));
        renderParts(spin, turbofan.afterburner() > 0, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    public static void renderParts(float spin, boolean afterburning, PoseStack poses,
                                   MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ThermalModelRenderer.render(BODY, poses, buffers, packedLight, packedOverlay);

        poses.pushPose();
        poses.translate(0.0D, 1.5D, 0.0D);
        poses.mulPose(Axis.ZN.rotationDegrees(spin));
        poses.translate(0.0D, -1.5D, 0.0D);
        ThermalModelRenderer.render(BLADES, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        ThermalModelRenderer.render(afterburning ? AFTERBURNER_HOT : AFTERBURNER_BACK,
                poses, buffers, packedLight, packedOverlay);
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

    @Override
    public AABB getRenderBoundingBox(TurbofanBlockEntity turbofan) {
        return turbofan.renderBounds();
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "block/" + path));
    }
}
