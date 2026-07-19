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

/** Additive celestial B92 bolt used during the staged moon-impact cinematic. */
public final class MoonImpactProjectileRenderer {
    private static final double SKY_DISTANCE = 90.0D;

    private MoonImpactProjectileRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MoonImpactProjectileRenderer::render);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY
                || !ClientMoonEvents.isApproachActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float age = ClientMoonEvents.approachAge(partialTick);
        if (age < ClientMoonEvents.FLIGHT_START) return;

        float progress = ClientMoonEvents.flightProgress(partialTick);
        float hold = ClientMoonEvents.holdProgress(partialTick);
        Vec3 head = ClientMoonEvents.projectileDirection(minecraft.level, progress,
                event.getCamera().getYRot()).scale(SKY_DISTANCE);
        float tailProgress = Math.max(0.0F, progress - 0.16F * (1.0F - hold));
        Vec3 tail = ClientMoonEvents.projectileDirection(minecraft.level, tailProgress,
                event.getCamera().getYRot()).scale(SKY_DISTANCE);

        Vector3f cameraLeft = event.getCamera().getLeftVector();
        Vector3f cameraUp = event.getCamera().getUpVector();
        Vec3 left = new Vec3(cameraLeft.x, cameraLeft.y, cameraLeft.z).normalize();
        Vec3 up = new Vec3(cameraUp.x, cameraUp.y, cameraUp.z).normalize();
        float travelSize = Mth.lerp(progress, 1.35F, 0.42F);
        float pulse = hold > 0.0F ? 1.0F + Mth.sin(age * 1.15F) * 0.28F : 1.0F;
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
            ribbon(builder, event.getModelViewMatrix(), tail, head, left, travelSize * 1.8F,
                    54, 92, 255, Math.round(115.0F * trailAlpha));
            ribbon(builder, event.getModelViewMatrix(), tail, head, left, travelSize * 0.48F,
                    210, 232, 255, Math.round(245.0F * trailAlpha));
        }
        billboard(builder, event.getModelViewMatrix(), head, left, up, travelSize * pulse * 3.1F,
                35, 70, 255, 92);
        billboard(builder, event.getModelViewMatrix(), head, left, up, travelSize * pulse * 1.55F,
                84, 154, 255, 185);
        billboard(builder, event.getModelViewMatrix(), head, left, up, travelSize * pulse * 0.62F,
                238, 248, 255, 255);
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
