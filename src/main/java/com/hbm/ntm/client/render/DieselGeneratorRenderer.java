package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.DieselGeneratorBlock;
import com.hbm.ntm.blockentity.DieselGeneratorBlockEntity;
import com.hbm.ntm.client.sound.DieselGeneratorSoundInstance;
import com.hbm.ntm.recipe.DieselGeneratorFuels;
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

import java.util.Map;
import java.util.WeakHashMap;

public final class DieselGeneratorRenderer implements BlockEntityRenderer<DieselGeneratorBlockEntity> {
    public static final ModelResourceLocation GENERATOR = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/dieselgen_generator"));
    public static final ModelResourceLocation ENGINE = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/dieselgen_engine"));
    private final Map<DieselGeneratorBlockEntity, DieselGeneratorSoundInstance> sounds = new WeakHashMap<>();

    public DieselGeneratorRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(DieselGeneratorBlockEntity generator, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        sounds.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (generator.active() && !sounds.containsKey(generator)) {
            DieselGeneratorSoundInstance sound = new DieselGeneratorSoundInstance(generator);
            sounds.put(generator, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }

        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(facingRotation(
                generator.getBlockState().getValue(DieselGeneratorBlock.FACING))));
        renderModel(pose, buffers, light, overlay, GENERATOR);
        if (generator.fuelAmount() > 0 && DieselGeneratorFuels.accepted(generator.selectedFluid())) {
            pose.translate(Math.sin(System.currentTimeMillis() / 25D) * 0.005D, 0D,
                    Math.sin(System.currentTimeMillis() / 50D) * 0.005D);
        }
        renderModel(pose, buffers, light, overlay, ENGINE);
        pose.popPose();
    }

    private static void renderModel(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                    ModelResourceLocation location) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(location);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.solidBlockSheet());
        renderer.renderModel(pose.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                1F, 1F, 1F, light, overlay);
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

    @Override public AABB getRenderBoundingBox(DieselGeneratorBlockEntity generator) {
        BlockPos pos = generator.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1D, pos.getY() + 1D, pos.getZ() + 1D);
    }
}
