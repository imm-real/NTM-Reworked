package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.SteelFurnaceBlock;
import com.hbm.ntm.blockentity.SteelFurnaceBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Original OBJ plus its four additive orange fire planes. */
public final class SteelFurnaceRenderer implements BlockEntityRenderer<SteelFurnaceBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/furnace_steel_body"));
    private static final RenderType FIRE = RenderType.create(
            "hbm_steel_furnace_fire", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    public SteelFurnaceRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(SteelFurnaceBlockEntity furnace, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(rotation(
                furnace.getBlockState().getValue(SteelFurnaceBlock.FACING))));
        ThermalModelRenderer.render(MODEL, poses, buffers, packedLight, packedOverlay);
        if (furnace.wasOn()) renderFire(poses.last(), buffers.getBuffer(FIRE));
        poses.popPose();
    }

    private static void renderFire(PoseStack.Pose pose, VertexConsumer consumer) {
        float oscillation = (float) Math.sin(Util.getMillis() * 0.001D);
        float red = 0.875F + oscillation * 0.125F;
        float green = 0.625F + oscillation * 0.375F;
        for (int plane = 0; plane < 4; plane++) {
            float x = 1.0F + plane * 0.0625F;
            vertex(consumer, pose, x, 1.0F, -1.0F, red, green);
            vertex(consumer, pose, x, 1.0F, 1.0F, red, green);
            vertex(consumer, pose, x, 0.5F, 1.0F, red, green);
            vertex(consumer, pose, x, 0.5F, -1.0F, red, green);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float red, float green) {
        consumer.addVertex(pose, x, y, z).setColor(red, green, 0.0F, 0.5F);
    }

    private static float rotation(Direction facing) {
        return switch (facing) {
            case EAST -> 0F;
            case NORTH -> 90F;
            case WEST -> 180F;
            case SOUTH -> 270F;
            default -> 0F;
        };
    }

    @Override public AABB getRenderBoundingBox(SteelFurnaceBlockEntity furnace) {
        BlockPos pos = furnace.getBlockPos();
        return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 3, pos.getZ() + 2);
    }

    @Override public int getViewDistance() { return 256; }
}
