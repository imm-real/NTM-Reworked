package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.fluid.FluidTankProperties;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws the tiny hazard diamonds that make every GUI feel safer. */
final class HazardDiamondRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/models/misc/danger_diamond.png");
    private static final float PIXEL = 1F / 256F;
    private static final float SECTION = 1F / 139F;

    private HazardDiamondRenderer() { }

    static void render(FluidTankProperties.Profile profile, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        PoseStack.Pose pose = poses.last();

        quad(consumer, pose,
                .5F, -.5F, 144F * PIXEL, 45F * PIXEL,
                -.5F, .5F, 5F * PIXEL, 184F * PIXEL, light);

        number(consumer, pose, profile.health(), 0F, 33F * SECTION, light);
        number(consumer, pose, profile.flammability(), 33F * SECTION, 0F, light);
        number(consumer, pose, profile.reactivity(), 0F, -33F * SECTION, light);

        FluidTankProperties.Symbol symbol = profile.symbol();
        if (symbol != FluidTankProperties.Symbol.NONE) {
            float size = 59F * .5F * SECTION;
            float centerY = -33F * SECTION;
            float minU = symbol.x() * PIXEL;
            float minV = symbol.y() * PIXEL;
            quad(consumer, pose,
                    centerY + size, -size, minU + 59F * PIXEL, minV,
                    centerY - size, size, minU, minV + 59F * PIXEL, light);
        }
    }

    private static void number(VertexConsumer consumer, PoseStack.Pose pose, int number,
                               float centerY, float centerZ, int light) {
        if (number < 0 || number >= 6) return;
        float width = 10F * SECTION;
        float height = 14F * SECTION;
        int x = number == 0 ? 125 : 5 + (number - 1) * 24;
        int y = 5;
        quad(consumer, pose,
                centerY + height, centerZ - width, (x + 20F) * PIXEL, y * PIXEL,
                centerY - height, centerZ + width, x * PIXEL, (y + 28F) * PIXEL, light);
    }

    /** Emits the plane at X=0.01 with its unusually important UV corner order. */
    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float top, float left, float topLeftU, float topLeftV,
                             float bottom, float right, float bottomRightU, float bottomRightV,
                             int light) {
        vertex(consumer, pose, top, left, topLeftU, topLeftV, light);
        vertex(consumer, pose, top, right, bottomRightU, topLeftV, light);
        vertex(consumer, pose, bottom, right, bottomRightU, bottomRightV, light);
        vertex(consumer, pose, bottom, left, topLeftU, bottomRightV, light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float y, float z, float u, float v, int light) {
        consumer.addVertex(pose, .01F, y, z).setColor(1F, 1F, 1F, 1F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1F, 0F, 0F);
    }
}
