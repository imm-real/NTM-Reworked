package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.HeatBoilerBlock;
import com.hbm.ntm.blockentity.HeatBoilerBlockEntity;
import com.hbm.ntm.client.sound.HeatBoilerSoundInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

public final class HeatBoilerRenderer implements BlockEntityRenderer<HeatBoilerBlockEntity> {
    public static final ModelResourceLocation NORMAL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/boiler"));
    public static final ModelResourceLocation BURST = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/boiler_burst"));
    private final Map<BlockPos, HeatBoilerSoundInstance> sounds = new HashMap<>();

    public HeatBoilerRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(HeatBoilerBlockEntity boiler, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        sounds.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (boiler.active() && !boiler.hasExploded()) {
            HeatBoilerSoundInstance sound = sounds.get(boiler.getBlockPos());
            if (sound == null || sound.isStopped()) {
                sound = new HeatBoilerSoundInstance(boiler);
                sounds.put(boiler.getBlockPos().immutable(), sound);
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        }
        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(facingRotation(
                boiler.getBlockState().getValue(HeatBoilerBlock.FACING))));
        if (!boiler.hasExploded()
                && boiler.outputTank().getFluidAmount() > boiler.outputTank().getCapacity() * 0.9D) {
            double sine = Math.sin(System.currentTimeMillis() / 50D % (Math.PI * 2D)) * 0.01D;
            pose.scale((float) (1D - sine), (float) (1D + sine), (float) (1D - sine));
        }
        renderModel(boiler.hasExploded() ? BURST : NORMAL, pose, buffers, light, overlay);
        pose.popPose();
    }

    private void renderModel(ModelResourceLocation id, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(id);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.solidBlockSheet());
        renderer.renderModel(pose.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                1F, 1F, 1F, light, overlay);
    }

    private static float facingRotation(Direction direction) {
        return switch (direction) {
            case NORTH -> 180F;
            case EAST -> 270F;
            case SOUTH -> 0F;
            case WEST -> 90F;
            default -> 0F;
        };
    }

    @Override public AABB getRenderBoundingBox(HeatBoilerBlockEntity boiler) {
        BlockPos pos = boiler.getBlockPos();
        return new AABB(pos.getX() - 1D, pos.getY(), pos.getZ() - 1D,
                pos.getX() + 2D, pos.getY() + 4D, pos.getZ() + 2D);
    }

    @Override public int getViewDistance() { return 256; }
}
