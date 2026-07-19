package com.hbm.ntm.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Red-white B93 solar bolt used during the sun-impact cinematic. */
public final class SolarImpactProjectileRenderer {
    private static final double SKY_DISTANCE = 90.0D;

    private SolarImpactProjectileRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SolarImpactProjectileRenderer::render);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY
                || !ClientSunEvents.isApproachActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float age = ClientSunEvents.approachAge(partialTick);
        if (age < ClientSunEvents.FLIGHT_START) return;

        float progress = ClientSunEvents.flightProgress(partialTick);
        float hold = ClientSunEvents.holdProgress(partialTick);
        Vec3 head = ClientSunEvents.projectileDirection(minecraft.level, progress,
                event.getCamera().getYRot()).scale(SKY_DISTANCE);
        float tailProgress = Math.max(0.0F, progress - 0.19F * (1.0F - hold));
        Vec3 tail = ClientSunEvents.projectileDirection(minecraft.level, tailProgress,
                event.getCamera().getYRot()).scale(SKY_DISTANCE);

        Vector3f cameraLeft = event.getCamera().getLeftVector();
        Vector3f cameraUp = event.getCamera().getUpVector();
        Vec3 left = new Vec3(cameraLeft.x, cameraLeft.y, cameraLeft.z).normalize();
        Vec3 up = new Vec3(cameraUp.x, cameraUp.y, cameraUp.z).normalize();
        float travelSize = Mth.lerp(progress, 1.65F, 0.50F);
        float pulse = hold > 0.0F ? 1.0F + Mth.sin(age * 1.42F) * 0.34F : 1.0F;
        float trailAlpha = 1.0F - hold;

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (trailAlpha > 0.0F && tail.distanceToSqr(head) > 1.0E-4D) {
            ribbon(builder, event.getModelViewMatrix(), tail, head, left, travelSize * 2.25F,
                    255, 12, 4, Math.round(135.0F * trailAlpha));
            ribbon(builder, event.getModelViewMatrix(), tail, head, left, travelSize * 1.05F,
                    255, 92, 18, Math.round(225.0F * trailAlpha));
            ribbon(builder, event.getModelViewMatrix(), tail, head, left, travelSize * 0.42F,
                    255, 242, 208, Math.round(255.0F * trailAlpha));
        }
        billboard(builder, event.getModelViewMatrix(), head, left, up, travelSize * pulse * 4.2F,
                255, 12, 0, 105);
        billboard(builder, event.getModelViewMatrix(), head, left, up, travelSize * pulse * 2.25F,
                255, 98, 16, 210);
        billboard(builder, event.getModelViewMatrix(), head, left, up, travelSize * pulse * 0.82F,
                255, 252, 234, 255);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    private static void ribbon(BufferBuilder builder, Matrix4f matrix, Vec3 tail, Vec3 head, Vec3 left,
                               float halfWidth, int red, int green, int blue, int alpha) {
        Vec3 side = left.scale(halfWidth);
        vertex(builder, matrix, tail.add(side), red, green, blue, 0);
        vertex(builder, matrix, head.add(side), red, green, blue, alpha);
        vertex(builder, matrix, head.subtract(side), red, green, blue, alpha);
        vertex(builder, matrix, tail.subtract(side), red, green, blue, 0);
    }

    private static void billboard(BufferBuilder builder, Matrix4f matrix, Vec3 center, Vec3 left, Vec3 up,
                                  float halfSize, int red, int green, int blue, int alpha) {
        Vec3 horizontal = left.scale(halfSize);
        Vec3 vertical = up.scale(halfSize);
        vertex(builder, matrix, center.add(horizontal).add(vertical), red, green, blue, alpha);
        vertex(builder, matrix, center.subtract(horizontal).add(vertical), red, green, blue, alpha);
        vertex(builder, matrix, center.subtract(horizontal).subtract(vertical), red, green, blue, alpha);
        vertex(builder, matrix, center.add(horizontal).subtract(vertical), red, green, blue, alpha);
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 position,
                               int red, int green, int blue, int alpha) {
        builder.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .setColor(red, green, blue, alpha);
    }
}
