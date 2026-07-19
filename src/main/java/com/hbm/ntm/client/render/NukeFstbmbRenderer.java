package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.NukeBalefireBlock;
import com.hbm.ntm.blockentity.NukeBalefireBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Draws the FSTBMB body, balefire parts and floating red countdown. The scrolling
 * glintBF overlay still needs a renderer that does not depend on an ancient texture matrix.
 */
public final class NukeFstbmbRenderer implements BlockEntityRenderer<NukeBalefireBlockEntity> {
    public static final ModelResourceLocation BODY = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/fstbmb_body"));
    public static final ModelResourceLocation BALEFIRE = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/fstbmb_balefire"));

    public NukeFstbmbRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(NukeBalefireBlockEntity bomb, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);

        // Source meta rotation {2->90, 4->180, 3->270, 5->0} == {N->90, W->180, S->270, E->0}.
        poses.mulPose(Axis.YP.rotationDegrees(facingRotation(bomb.getBlockState().getValue(NukeBalefireBlock.FACING))));

        ThermalModelRenderer.render(BODY, poses, buffers, packedLight, packedOverlay);
        ThermalModelRenderer.render(BALEFIRE, poses, buffers, packedLight, packedOverlay);

        if (bomb.loaded) {
            Font font = Minecraft.getInstance().font;
            String time = bomb.getMinutes() + ":" + bomb.getSeconds();
            float scale = 0.04F;
            poses.pushPose();
            poses.translate(0.815D, 0.9275D, 0.5D);
            poses.scale(scale, -scale, scale);
            poses.mulPose(Axis.YP.rotationDegrees(90.0F));
            poses.translate(0.0D, 1.0D, 0.0D);
            font.drawInBatch(time, 0.0F, 0.0F, 0xFF0000, false, poses.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            poses.popPose();
        }

        poses.popPose();
    }

    private static float facingRotation(Direction facing) {
        return switch (facing) {
            case NORTH -> 90.0F;
            case WEST -> 180.0F;
            case SOUTH -> 270.0F;
            default -> 0.0F;
        };
    }

    @Override
    public AABB getRenderBoundingBox(NukeBalefireBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX() - 3, pos.getY() - 1, pos.getZ() - 3,
                pos.getX() + 4, pos.getY() + 4, pos.getZ() + 4);
    }

    @Override public int getViewDistance() { return 256; }
}
