package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.DynamicSlagBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Variable-height tinted slag volume used by the spill outlet. */
public final class DynamicSlagRenderer implements BlockEntityRenderer<DynamicSlagBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/block/slag.png");

    public DynamicSlagRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(DynamicSlagBlockEntity slag, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        if (slag.material() == null || slag.amount() <= 0) return;
        int color = slag.material().moltenColor();
        float red = (color >> 16 & 255) / 255F;
        float green = (color >> 8 & 255) / 255F;
        float blue = (color & 255) / 255F;
        float y = (float) slag.height();
        float inset = .001F;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        PoseStack.Pose current = pose.last();

        quad(consumer, current, inset, y, inset, inset, y, 1F - inset,
                1F - inset, y, 1F - inset, 1F - inset, y, inset, red, green, blue, 0, 1, 0);
        side(consumer, current, inset, inset, 1F - inset, y, red, green, blue, true, false);
        side(consumer, current, 1F - inset, inset, inset, y, red, green, blue, true, true);
        side(consumer, current, inset, inset, inset, y, red, green, blue, false, false);
        side(consumer, current, inset, 1F - inset, 1F - inset, y, red, green, blue, false, true);
    }

    private static void side(VertexConsumer consumer, PoseStack.Pose pose, float x, float z,
                             float end, float y, float red, float green, float blue,
                             boolean alongZ, boolean reverse) {
        float x2 = alongZ ? x : end;
        float z2 = alongZ ? end : z;
        if (reverse) quad(consumer, pose, x2, 0, z2, x2, y, z2, x, y, z, x, 0, z,
                red, green, blue, 0, 0, 1);
        else quad(consumer, pose, x, 0, z, x, y, z, x2, y, z2, x2, 0, z2,
                red, green, blue, 0, 0, 1);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float red, float green, float blue, float nx, float ny, float nz) {
        vertex(consumer, pose, x1, y1, z1, 0, 0, red, green, blue, nx, ny, nz);
        vertex(consumer, pose, x2, y2, z2, 0, 1, red, green, blue, nx, ny, nz);
        vertex(consumer, pose, x3, y3, z3, 1, 1, red, green, blue, nx, ny, nz);
        vertex(consumer, pose, x4, y4, z4, 1, 0, red, green, blue, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, float red, float green, float blue,
                               float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z).setColor(red, green, blue, 1F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }
}
