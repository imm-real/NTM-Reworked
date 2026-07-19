package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.RefineryBlockEntity;
import com.hbm.ntm.client.sound.RefinerySoundInstance;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

/** Refinery model and boiler sound loop. */
public final class RefineryRenderer implements BlockEntityRenderer<RefineryBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/refinery"));

    private final Map<RefineryBlockEntity, RefinerySoundInstance> sounds = new WeakHashMap<>();

    public RefineryRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(RefineryBlockEntity refinery, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        sounds.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (refinery.active()) {
            RefinerySoundInstance sound = sounds.get(refinery);
            if (sound == null || sound.isStopped()) {
                sound = new RefinerySoundInstance(refinery);
                sounds.put(refinery, sound);
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        }
        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(180F));

        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(MODEL);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.solidBlockSheet());
        renderer.renderModel(pose.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                1F, 1F, 1F, light, overlay);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(RefineryBlockEntity refinery) {
        BlockPos pos = refinery.getBlockPos();
        return new AABB(pos.getX() - 1D, pos.getY(), pos.getZ() - 1D,
                pos.getX() + 2D, pos.getY() + 10D, pos.getZ() + 2D);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
