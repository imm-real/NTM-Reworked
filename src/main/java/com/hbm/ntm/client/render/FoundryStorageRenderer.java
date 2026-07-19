package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.FoundryChannelBlock;
import com.hbm.ntm.blockentity.AbstractFoundryBlockEntity;
import com.hbm.ntm.blockentity.FoundryChannelBlockEntity;
import com.hbm.ntm.blockentity.FoundryTankBlockEntity;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Full-bright molten surfaces for foundry channels and storage basins. */
public final class FoundryStorageRenderer<T extends AbstractFoundryBlockEntity> implements BlockEntityRenderer<T> {
    private static final ResourceLocation LAVA = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/models/machines/lava_gray.png");

    public FoundryStorageRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(T storage, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay) {
        FoundryMaterial material = storage.material();
        if (material == null || storage.amount() <= 0) return;
        int color = material.moltenColor();
        float red = (color >> 16 & 255) / 255F;
        float green = (color >> 8 & 255) / 255F;
        float blue = (color & 255) / 255F;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(LAVA));
        PoseStack.Pose current = pose.last();

        if (storage instanceof FoundryChannelBlockEntity channel) {
            float y = (float) channel.moltenSurfaceHeight();
            quad(consumer, current, .375F, .375F, .625F, .625F, y, red, green, blue);
            BlockState state = channel.getBlockState();
            if (state.getValue(FoundryChannelBlock.NORTH))
                quad(consumer, current, .3125F, 0F, .6875F, .375F, y, red, green, blue);
            if (state.getValue(FoundryChannelBlock.EAST))
                quad(consumer, current, .625F, .3125F, 1F, .6875F, y, red, green, blue);
            if (state.getValue(FoundryChannelBlock.SOUTH))
                quad(consumer, current, .3125F, .625F, .6875F, 1F, y, red, green, blue);
            if (state.getValue(FoundryChannelBlock.WEST))
                quad(consumer, current, 0F, .3125F, .375F, .6875F, y, red, green, blue);
        } else if (storage instanceof FoundryTankBlockEntity tank) {
            quad(consumer, current, .125F, .125F, .875F, .875F,
                    (float) tank.moltenSurfaceHeight(), red, green, blue);
        }
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float minX, float minZ, float maxX, float maxZ, float y,
                             float red, float green, float blue) {
        vertex(consumer, pose, minX, y, minZ, 0, 0, red, green, blue);
        vertex(consumer, pose, minX, y, maxZ, 0, 1, red, green, blue);
        vertex(consumer, pose, maxX, y, maxZ, 1, 1, red, green, blue);
        vertex(consumer, pose, maxX, y, minZ, 1, 0, red, green, blue);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, float red, float green, float blue) {
        consumer.addVertex(pose, x, y, z).setColor(red, green, blue, 1F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0F, 1F, 0F);
    }
}
