package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.CentrifugeBlock;
import com.hbm.ntm.blockentity.CentrifugeBlockEntity;
import com.hbm.ntm.client.sound.CentrifugeSoundInstance;
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

public final class CentrifugeRenderer implements BlockEntityRenderer<CentrifugeBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/centrifuge"));
    private final Map<CentrifugeBlockEntity, CentrifugeSoundInstance> sounds = new WeakHashMap<>();

    public CentrifugeRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(CentrifugeBlockEntity centrifuge, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        sounds.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (centrifuge.active() && !sounds.containsKey(centrifuge)) {
            CentrifugeSoundInstance sound = new CentrifugeSoundInstance(centrifuge);
            sounds.put(centrifuge, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }

        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(facingRotation(
                centrifuge.getBlockState().getValue(CentrifugeBlock.FACING))));
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
            case NORTH -> 90F;
            case WEST -> 180F;
            case SOUTH -> 270F;
            case EAST -> 0F;
            default -> 0F;
        };
    }

    @Override public AABB getRenderBoundingBox(CentrifugeBlockEntity centrifuge) {
        BlockPos pos = centrifuge.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1D, pos.getY() + 4D, pos.getZ() + 1D);
    }

    @Override public int getViewDistance() { return 256; }
}
