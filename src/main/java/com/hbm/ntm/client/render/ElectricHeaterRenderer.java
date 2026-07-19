package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ElectricHeaterBlock;
import com.hbm.ntm.blockentity.ElectricHeaterBlockEntity;
import com.hbm.ntm.client.sound.ElectricHeaterSoundInstance;
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

public final class ElectricHeaterRenderer implements BlockEntityRenderer<ElectricHeaterBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/electric_heater_obj"));
    private final Map<BlockPos, ElectricHeaterSoundInstance> sounds = new HashMap<>();

    public ElectricHeaterRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(ElectricHeaterBlockEntity heater, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        sounds.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (heater.active()) {
            ElectricHeaterSoundInstance sound = sounds.get(heater.getBlockPos());
            if (sound == null || sound.isStopped()) {
                sound = new ElectricHeaterSoundInstance(heater);
                sounds.put(heater.getBlockPos().immutable(), sound);
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        }

        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(facingRotation(
                heater.getBlockState().getValue(ElectricHeaterBlock.FACING))));
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(MODEL);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.solidBlockSheet());
        renderer.renderModel(pose.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                1F, 1F, 1F, light, overlay);
        pose.popPose();
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

    @Override public AABB getRenderBoundingBox(ElectricHeaterBlockEntity heater) {
        BlockPos pos = heater.getBlockPos();
        return new AABB(pos.getX() - 2D, pos.getY(), pos.getZ() - 2D,
                pos.getX() + 3D, pos.getY() + 1D, pos.getZ() + 3D);
    }

    @Override public int getViewDistance() { return 256; }
}
