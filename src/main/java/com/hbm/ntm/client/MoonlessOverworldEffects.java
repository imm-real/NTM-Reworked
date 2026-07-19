package com.hbm.ntm.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Overworld sky after the moon has left the group chat. */
public final class MoonlessOverworldEffects extends DimensionSpecialEffects.OverworldEffects {
    private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final ResourceLocation FLASH = ResourceLocation.withDefaultNamespace("textures/particle/flash.png");
    private static final float SKY_RADIUS = 100.0F;
    private static final int MOON_FRAGMENT_GRID = 5;
    private static final int[][] SOLAR_PALETTE = {
            {255, 46, 18}, {255, 132, 20}, {255, 236, 82}, {126, 255, 78},
            {64, 238, 255}, {72, 126, 255}, {158, 82, 255}, {255, 70, 194}
    };

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix,
                             Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        boolean moonRemoved = ClientMoonEvents.isDestroyed() && !ClientMoonEvents.isApproachActive();
        boolean sunRemoved = ClientSunEvents.isDestroyed() && !ClientSunEvents.isApproachActive();
        if ((!moonRemoved && !sunRemoved) || level.dimension() != Level.OVERWORLD) return false;

        setupFog.run();
        if (isFoggy || camera.getFluidInCamera() == FogType.LAVA
                || camera.getFluidInCamera() == FogType.POWDER_SNOW || skyBlocked(camera)) {
            return true;
        }

        PoseStack poses = new PoseStack();
        poses.mulPose(modelViewMatrix);
        boolean blackSky = shouldBlacken(level);
        Vec3 skyColor = blackSky ? Vec3.ZERO : level.getSkyColor(camera.getPosition(), partialTick);

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        drawSkyCube(poses.last().pose(), skyColor);
        if (!sunRemoved && !ClientMoonEvents.shouldBlacken(level)) drawSun(level, partialTick, poses);
        if (!moonRemoved) drawMoon(level, partialTick, poses);
        if (ClientMoonEvents.isExploding()) drawMoonExplosion(level, partialTick, poses);
        if (ClientSunEvents.isExploding()) drawSunExplosion(level, partialTick, poses);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        return true;
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && shouldBlacken(level)
                ? Vec3.ZERO
                : super.getBrightnessDependentFogColor(color, brightness);
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken,
                                     float blockLightRedFlicker, float skyLight, int pixelX, int pixelY,
                                     Vector3f colors) {
        if (!shouldBlacken(level)) return;

        // Keep torches. Delete ambient optimism.
        float block = LightTexture.getBrightness(level.dimensionType(), pixelX) * blockLightRedFlicker;
        float green = block * ((block * 0.6F + 0.4F) * 0.6F + 0.4F);
        float blue = block * (block * block * 0.6F + 0.4F);
        colors.set(Mth.clamp(block, 0.0F, 1.0F), Mth.clamp(green, 0.0F, 1.0F),
                Mth.clamp(blue, 0.0F, 1.0F));
    }

    private static boolean shouldBlacken(ClientLevel level) {
        return ClientMoonEvents.shouldBlacken(level) || ClientSunEvents.shouldBlacken(level);
    }

    private static boolean skyBlocked(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    private static void drawSkyCube(Matrix4f matrix, Vec3 color) {
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int red = Mth.clamp((int) Math.round(color.x * 255.0D), 0, 255);
        int green = Mth.clamp((int) Math.round(color.y * 255.0D), 0, 255);
        int blue = Mth.clamp((int) Math.round(color.z * 255.0D), 0, 255);
        float s = SKY_RADIUS;
        float[][] vertices = {
                {-s, -s, -s}, {-s, -s, s}, {-s, s, s}, {-s, s, -s},
                {s, -s, s}, {s, -s, -s}, {s, s, -s}, {s, s, s},
                {-s, -s, s}, {s, -s, s}, {s, s, s}, {-s, s, s},
                {s, -s, -s}, {-s, -s, -s}, {-s, s, -s}, {s, s, -s},
                {-s, s, s}, {s, s, s}, {s, s, -s}, {-s, s, -s},
                {-s, -s, -s}, {s, -s, -s}, {s, -s, s}, {-s, -s, s}
        };
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] vertex : vertices) {
            builder.addVertex(matrix, vertex[0], vertex[1], vertex[2]).setColor(red, green, blue, 255);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void drawSun(ClientLevel level, float partialTick, PoseStack poses) {
        poses.pushPose();
        orientCelestialPlane(level, partialTick, poses);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SUN);
        int alpha = Math.round((1.0F - level.getRainLevel(partialTick)) * 255.0F);
        drawTexturedQuad(poses.last().pose(), 0.0F, 100.0F, 0.0F, 30.0F, 255, 255, 255, alpha);
        poses.popPose();
    }

    private static void drawMoon(ClientLevel level, float partialTick, PoseStack poses) {
        poses.pushPose();
        orientCelestialPlane(level, partialTick, poses);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, MOON);
        int phase = level.getMoonPhase();
        float u0 = (phase % 4) * 0.25F;
        float v0 = ((phase / 4) % 2) * 0.5F;
        float u1 = u0 + 0.25F;
        float v1 = v0 + 0.5F;
        int alpha = Math.round((1.0F - level.getRainLevel(partialTick)) * 255.0F);
        Matrix4f matrix = poses.last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(matrix, -20.0F, -100.0F, 20.0F).setUv(u1, v1)
                .setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, 20.0F, -100.0F, 20.0F).setUv(u0, v1)
                .setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, 20.0F, -100.0F, -20.0F).setUv(u0, v0)
                .setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, -20.0F, -100.0F, -20.0F).setUv(u1, v0)
                .setColor(255, 255, 255, alpha);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        poses.popPose();
    }

    private static void drawSunExplosion(ClientLevel level, float partialTick, PoseStack poses) {
        float age = ClientSunEvents.explosionAge(partialTick);
        float life = Mth.clamp(1.0F - age / ClientSunEvents.EXPLOSION_DURATION, 0.0F, 1.0F);
        float birth = Mth.clamp(age / 3.0F, 0.0F, 1.0F);
        float pulse = 1.0F + Mth.sin(age * 0.82F) * 0.075F;

        poses.pushPose();
        orientCelestialPlane(level, partialTick, poses);
        Matrix4f matrix = poses.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, FLASH);

        // Flat sky gets the halo; the actual tantrum is geometry below.
        int[] shellColor = solarColor((int) Math.floor(age / 5.0F));
        drawTexturedQuad(matrix, 0.0F, 99.0F, 0.0F, (34.0F + age * 1.05F) * pulse * birth,
                shellColor[0], shellColor[1], shellColor[2], Math.round(38.0F * life));
        drawTexturedQuad(matrix, 0.0F, 98.8F, 0.0F, (13.0F + age * 0.12F) * birth,
                255, 255, 246, Math.round(72.0F * life * life));

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        if (age > 0.0F) {
            drawSolarVolume(matrix, age, life, birth);
            drawSolarJets(matrix, age, life, birth);
        }
        drawSolarShockwaves(matrix, age, life);
        poses.popPose();
    }

    private static void drawSolarVolume(Matrix4f matrix, float age, float life, float birth) {
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int[] shellColor = solarColor((int) Math.floor(age / 5.0F));
        int[] counterColor = solarColor((int) Math.floor(age / 5.0F) + 4);

        // Three rippling shells stop the sun from looking like a dinner plate.
        appendTurbulentSphere(builder, matrix, 0.0F, 98.15F, 0.0F,
                (11.0F + age * 0.08F) * birth, age, 255, 252, 235,
                Math.round(255.0F * life), 0, 12, 24);
        appendTurbulentSphere(builder, matrix, 0.0F, 98.2F, 0.0F,
                (20.0F + age * 0.13F) * birth, age,
                shellColor[0], shellColor[1], shellColor[2],
                Math.round(225.0F * life), 2, 12, 24);
        appendTurbulentSphere(builder, matrix, 0.0F, 98.25F, 0.0F,
                (29.0F + age * 0.18F) * birth, age,
                counterColor[0], counterColor[1], counterColor[2],
                Math.round(175.0F * life), 5, 12, 24);

        appendSolarShockSphere(builder, matrix, age, 0.0F, 1.28F, 74.0F, 0);
        appendSolarShockSphere(builder, matrix, age, ClientSunEvents.IGNITION_TICK,
                1.52F, 76.0F, 1);
        appendSolarShockSphere(builder, matrix, age, ClientSunEvents.PHOTOSPHERE_TICK,
                1.78F, 88.0F, 2);
        appendSolarShockSphere(builder, matrix, age, ClientSunEvents.HYPERNOVA_TICK,
                2.12F, 106.0F, 3);
        appendSolarShockSphere(builder, matrix, age, ClientSunEvents.RUPTURE_TICK,
                2.48F, 124.0F, 4);
        appendSolarShockSphere(builder, matrix, age, ClientSunEvents.AFTERSHOCK_TICK,
                2.82F, 112.0F, 5);

        appendSolarLobes(builder, matrix, age, 0.0F, 0);
        appendSolarLobes(builder, matrix, age, ClientSunEvents.IGNITION_TICK, 1);
        appendSolarLobes(builder, matrix, age, ClientSunEvents.PHOTOSPHERE_TICK, 2);
        appendSolarLobes(builder, matrix, age, ClientSunEvents.HYPERNOVA_TICK, 3);
        appendSolarLobes(builder, matrix, age, ClientSunEvents.RUPTURE_TICK, 4);
        appendSolarLobes(builder, matrix, age, ClientSunEvents.AFTERSHOCK_TICK, 5);
        uploadIfPresent(builder);
    }

    private static void appendSolarShockSphere(BufferBuilder builder, Matrix4f matrix, float age,
                                               float start, float speed, float duration, int salt) {
        float localAge = age - start;
        if (localAge < 0.0F || localAge >= duration) return;
        float attack = Mth.clamp(localAge / 1.8F, 0.0F, 1.0F);
        float decay = Mth.clamp(1.0F - localAge / duration, 0.0F, 1.0F);
        int[] color = solarColor(salt + (int) Math.floor(localAge / 7.0F));
        appendTurbulentSphere(builder, matrix, 0.0F, 98.3F, 0.0F,
                (15.0F + localAge * speed) * attack, age,
                color[0], color[1], color[2],
                Math.round(155.0F * attack * decay * decay), salt + 11, 9, 18);
    }

    private static void appendSolarLobes(BufferBuilder builder, Matrix4f matrix, float age,
                                         float start, int salt) {
        float stageAge = age - start;
        if (stageAge < 0.0F || stageAge >= 62.0F) return;
        for (int i = 0; i < 7; i++) {
            float localAge = stageAge - i * 1.15F;
            if (localAge <= 0.0F) continue;
            float attack = Mth.clamp(localAge / 1.8F, 0.0F, 1.0F);
            float decay = Mth.clamp(1.0F - localAge / 62.0F, 0.0F, 1.0F);
            double azimuth = i * 2.399963229728653D + salt * 0.73D + stageAge * 0.018D;
            float depthDirection = -0.52F + i / 6.0F * 1.04F;
            float radialDirection = Mth.sqrt(Math.max(0.0F, 1.0F - depthDirection * depthDirection));
            float directionX = (float) Math.cos(azimuth) * radialDirection;
            float directionZ = (float) Math.sin(azimuth) * radialDirection;
            float travel = 16.0F + localAge * (0.48F + salt * 0.035F);
            float radius = (4.5F + salt * 0.65F + localAge * 0.15F) * attack;
            int[] color = solarColor(i + salt * 2 + (int) Math.floor(localAge / 5.0F));
            appendTurbulentSphere(builder, matrix,
                    directionX * travel, 98.1F + depthDirection * travel,
                    directionZ * travel, radius, age,
                    color[0], color[1], color[2],
                    Math.round(205.0F * attack * decay), salt + i, 5, 9);
        }
    }

    private static void appendTurbulentSphere(BufferBuilder builder, Matrix4f matrix,
                                               float centerX, float centerY, float centerZ,
                                               float radius, float age,
                                               int red, int green, int blue, int alpha,
                                               int phase, int latitudeBands, int longitudeBands) {
        if (radius <= 0.01F || alpha <= 0) return;
        for (int latitude = 0; latitude < latitudeBands; latitude++) {
            float latitude0 = -Mth.HALF_PI + Mth.PI * latitude / latitudeBands;
            float latitude1 = -Mth.HALF_PI + Mth.PI * (latitude + 1) / latitudeBands;
            for (int longitude = 0; longitude < longitudeBands; longitude++) {
                float longitude0 = Mth.TWO_PI * longitude / longitudeBands;
                float longitude1 = Mth.TWO_PI * (longitude + 1) / longitudeBands;
                solarSphereVertex(builder, matrix, centerX, centerY, centerZ, radius, age,
                        latitude0, longitude0, red, green, blue, alpha, phase);
                solarSphereVertex(builder, matrix, centerX, centerY, centerZ, radius, age,
                        latitude1, longitude0, red, green, blue, alpha, phase);
                solarSphereVertex(builder, matrix, centerX, centerY, centerZ, radius, age,
                        latitude1, longitude1, red, green, blue, alpha, phase);
                solarSphereVertex(builder, matrix, centerX, centerY, centerZ, radius, age,
                        latitude0, longitude1, red, green, blue, alpha, phase);
            }
        }
    }

    private static void solarSphereVertex(BufferBuilder builder, Matrix4f matrix,
                                          float centerX, float centerY, float centerZ,
                                          float radius, float age, float latitude, float longitude,
                                          int red, int green, int blue, int alpha, int phase) {
        float cosineLatitude = Mth.cos(latitude);
        float normalX = cosineLatitude * Mth.cos(longitude);
        float normalY = Mth.sin(latitude);
        float normalZ = cosineLatitude * Mth.sin(longitude);
        float turbulence = 1.0F
                + Mth.sin(longitude * (4.0F + phase % 3) + age * 0.105F + phase) * cosineLatitude * 0.075F
                + Mth.sin(latitude * 7.0F - age * 0.073F + phase * 1.7F) * 0.045F;
        float displacedRadius = radius * turbulence;

        float facing = Mth.clamp(-normalY, 0.0F, 1.0F);
        float rim = Mth.sqrt(Math.max(0.0F, 1.0F - normalY * normalY));
        float shimmer = 0.5F + 0.5F * Mth.sin(longitude * 6.0F + latitude * 9.0F + age * 0.16F + phase);
        float brightness = 0.32F + facing * 0.62F + rim * 0.22F;
        float heat = shimmer * (0.16F + facing * 0.14F);
        int vertexRed = Mth.clamp(Math.round(red * brightness + 255.0F * heat), 0, 255);
        int vertexGreen = Mth.clamp(Math.round(green * brightness + 245.0F * heat), 0, 255);
        int vertexBlue = Mth.clamp(Math.round(blue * brightness + 228.0F * heat), 0, 255);
        int vertexAlpha = Mth.clamp(Math.round(alpha * (0.28F + facing * 0.58F + rim * 0.14F)), 0, 255);
        builder.addVertex(matrix,
                        centerX + normalX * displacedRadius,
                        centerY + normalY * displacedRadius,
                        centerZ + normalZ * displacedRadius)
                .setColor(vertexRed, vertexGreen, vertexBlue, vertexAlpha);
    }

    private static void drawSolarJets(Matrix4f matrix, float age, float life, float birth) {
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float spin = age * 0.022F;
        Vec3 axis = new Vec3(Mth.cos(spin), Mth.sin(age * 0.013F) * 0.34F, Mth.sin(spin)).normalize();
        Vec3 center = new Vec3(0.0D, 98.0D, 0.0D);
        float length = (58.0F + age * 1.28F) * birth;
        float radius = (7.0F + age * 0.055F) * birth;
        int[] color = solarColor((int) Math.floor(age / 4.0F) + 6);
        int alpha = Math.round(205.0F * life * (0.72F + 0.28F * Math.abs(Mth.sin(age * 0.31F))));
        appendSolarJet(builder, matrix, center, axis, length, radius,
                color[0], color[1], color[2], alpha);
        appendSolarJet(builder, matrix, center, axis.scale(-1.0D), length, radius,
                color[0], color[1], color[2], alpha);
        appendSolarJet(builder, matrix, center, axis, length * 1.14F, radius * 0.30F,
                255, 255, 246, Math.round(alpha * 0.92F));
        appendSolarJet(builder, matrix, center, axis.scale(-1.0D), length * 1.14F, radius * 0.30F,
                255, 255, 246, Math.round(alpha * 0.92F));
        uploadIfPresent(builder);
    }

    /** Three nested cone shells, because one cone looked like a traffic marker. */
    private static void appendSolarJet(BufferBuilder builder, Matrix4f matrix, Vec3 center, Vec3 direction,
                                       float length, float radius, int red, int green, int blue, int alpha) {
        if (length <= 0.01F || radius <= 0.01F || alpha <= 0) return;
        Vec3 reference = Math.abs(direction.y) < 0.90D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 vertical = direction.cross(side).normalize();
        int segments = 20;
        int rings = 7;
        int shells = 3;
        // Direction-derived phase keeps both jets from shimmering in lockstep.
        float phase = (float) (direction.x * 31.0D + direction.z * 17.0D);
        for (int shell = 0; shell < shells; shell++) {
            float shellFraction = (shell + 1) / (float) shells;
            float shellAlpha = alpha * (1.05F - shellFraction * 0.75F);
            for (int ring = 0; ring < rings; ring++) {
                float near = ring / (float) rings;
                float far = (ring + 1) / (float) rings;
                float nearAlpha = shellAlpha * jetLengthFade(near);
                float farAlpha = shellAlpha * jetLengthFade(far);
                if (nearAlpha < 1.0F && farAlpha < 1.0F) continue;
                float nearRadius = radius * shellFraction * (0.30F + 0.70F * near);
                float farRadius = radius * shellFraction * (0.30F + 0.70F * far);
                Vec3 nearCenter = center.add(direction.scale(near * length));
                Vec3 farCenter = center.add(direction.scale(far * length));
                for (int i = 0; i < segments; i++) {
                    double angle0 = Mth.TWO_PI * i / segments;
                    double angle1 = Mth.TWO_PI * (i + 1) / segments;
                    // Recycle the tasteful amount of solar wobble.
                    float flicker0 = 0.86F + 0.14F * Mth.sin((float) angle0 * 3.0F + near * 7.0F + phase);
                    float flicker1 = 0.86F + 0.14F * Mth.sin((float) angle1 * 3.0F + near * 7.0F + phase);
                    solarPositionColorVertex(builder, matrix,
                            jetRim(nearCenter, side, vertical, angle0, nearRadius),
                            red, green, blue, Math.round(nearAlpha * flicker0));
                    solarPositionColorVertex(builder, matrix,
                            jetRim(nearCenter, side, vertical, angle1, nearRadius),
                            red, green, blue, Math.round(nearAlpha * flicker1));
                    solarPositionColorVertex(builder, matrix,
                            jetRim(farCenter, side, vertical, angle1, farRadius),
                            red, green, blue, Math.round(farAlpha * flicker1));
                    solarPositionColorVertex(builder, matrix,
                            jetRim(farCenter, side, vertical, angle0, farRadius),
                            red, green, blue, Math.round(farAlpha * flicker0));
                }
            }
        }
    }

    /** Hide the cone base and murder it gently before the tip. */
    private static float jetLengthFade(float t) {
        float fadeIn = Mth.clamp(t / 0.14F, 0.0F, 1.0F);
        float fadeOut = 1.0F - t;
        return fadeIn * fadeOut * fadeOut;
    }

    private static Vec3 jetRim(Vec3 ringCenter, Vec3 side, Vec3 vertical, double angle, float radius) {
        return ringCenter.add(side.scale(Math.cos(angle) * radius))
                .add(vertical.scale(Math.sin(angle) * radius));
    }

    private static void solarPositionColorVertex(BufferBuilder builder, Matrix4f matrix, Vec3 position,
                                                  int red, int green, int blue, int alpha) {
        builder.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .setColor(red, green, blue, alpha);
    }

    private static void drawSolarShockwaves(Matrix4f matrix, float age, float life) {
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        solarRing(builder, matrix, 28.0F + age * 3.4F, 3.0F + life * 12.0F,
                Math.round(48.0F * life), 255, 168, 54);

        float rayLife = Mth.clamp(1.0F - age / 220.0F, 0.0F, 1.0F);
        for (int i = 0; i < 18; i++) {
            double angle = i * 2.399963229728653D + (debrisNoise(i, 220) - 0.5F) * 0.28F;
            float inner = 10.0F + age * (0.36F + debrisNoise(i, 221) * 0.28F);
            float length = 35.0F + age * (1.4F + debrisNoise(i, 222) * 1.8F);
            float width = 0.45F + debrisNoise(i, 223) * 1.25F;
            solarRay(builder, matrix, angle, inner, length, width,
                    Math.round(64.0F * rayLife), i);
        }
        uploadIfPresent(builder);
    }

    private static void solarRing(BufferBuilder builder, Matrix4f matrix, float radius, float width,
                                  int alpha, int red, int green, int blue) {
        // Soft edges keep the ring from revealing its sixty-four-sided fraud.
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            solarRingVertex(builder, matrix, a0, radius - width, red, green, blue, 0);
            solarRingVertex(builder, matrix, a1, radius - width, red, green, blue, 0);
            solarRingVertex(builder, matrix, a1, radius, red, green, blue, alpha);
            solarRingVertex(builder, matrix, a0, radius, red, green, blue, alpha);
            solarRingVertex(builder, matrix, a0, radius, red, green, blue, alpha);
            solarRingVertex(builder, matrix, a1, radius, red, green, blue, alpha);
            solarRingVertex(builder, matrix, a1, radius + width, red, green, blue, 0);
            solarRingVertex(builder, matrix, a0, radius + width, red, green, blue, 0);
        }
    }

    private static void solarRingVertex(BufferBuilder builder, Matrix4f matrix, double angle, float radius,
                                        int red, int green, int blue, int alpha) {
        builder.addVertex(matrix, (float) Math.cos(angle) * radius, 97.9F,
                        (float) Math.sin(angle) * radius)
                .setColor(red, green, blue, alpha);
    }

    private static void solarRay(BufferBuilder builder, Matrix4f matrix, double angle, float inner,
                                 float length, float halfWidth, int alpha, int index) {
        float directionX = (float) Math.cos(angle);
        float directionZ = (float) Math.sin(angle);
        float perpendicularX = -directionZ * halfWidth;
        float perpendicularZ = directionX * halfWidth;
        float outer = inner + length;
        int[] color = solarColor(index);
        int[] tipColor = solarColor(index + 2);
        builder.addVertex(matrix, directionX * inner + perpendicularX, 97.7F,
                        directionZ * inner + perpendicularZ)
                .setColor(color[0], color[1], color[2], alpha);
        builder.addVertex(matrix, directionX * outer + perpendicularX * 0.05F, 97.7F,
                        directionZ * outer + perpendicularZ * 0.05F)
                .setColor(tipColor[0], tipColor[1], tipColor[2], 0);
        builder.addVertex(matrix, directionX * outer - perpendicularX * 0.05F, 97.7F,
                        directionZ * outer - perpendicularZ * 0.05F)
                .setColor(tipColor[0], tipColor[1], tipColor[2], 0);
        builder.addVertex(matrix, directionX * inner - perpendicularX, 97.7F,
                        directionZ * inner - perpendicularZ)
                .setColor(color[0], color[1], color[2], alpha);
    }

    private static int[] solarColor(int index) {
        return SOLAR_PALETTE[Math.floorMod(index, SOLAR_PALETTE.length)];
    }

    private static void drawMoonExplosion(ClientLevel level, float partialTick, PoseStack poses) {
        float age = ClientMoonEvents.explosionAge(partialTick);
        float life = Mth.clamp(1.0F - age / ClientMoonEvents.EXPLOSION_DURATION, 0.0F, 1.0F);
        float burst = Mth.clamp(age / 5.0F, 0.0F, 1.0F);
        float pulse = 1.0F + 0.10F * Mth.sin(age * 0.9F);

        poses.pushPose();
        orientCelestialPlane(level, partialTick, poses);
        Matrix4f matrix = poses.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, FLASH);

        drawTexturedQuad(matrix, 0.0F, -99.0F, 0.0F, (27.0F + age * 1.15F) * burst,
                255, 38, 8, Math.round(185.0F * life));
        drawTexturedQuad(matrix, 0.0F, -98.8F, 0.0F, (18.0F + age * 0.68F) * pulse,
                255, 132, 24, Math.round(235.0F * life));
        drawTexturedQuad(matrix, 0.0F, -98.6F, 0.0F, (11.0F + age * 0.28F) * pulse,
                255, 252, 218, Math.round(255.0F * life * life));
        if (age > 0.0F) drawFireballBlooms(matrix, age);
        if (age >= ClientMoonEvents.SECONDARY_BURST_TICK) drawStagedDetonations(matrix, age);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        drawShockwaveAndSparks(matrix, age, life);

        // Smoke interrupts the glow; moon chunks draw last for one final cameo.
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, FLASH);
        if (age > 8.0F) drawSmokeBlooms(matrix, age);
        RenderSystem.setShaderTexture(0, MOON);
        drawMoonFragments(level, matrix, age);
        poses.popPose();
    }

    private static void drawFireballBlooms(Matrix4f matrix, float age) {
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < 16; i++) {
            float delay = (i % 6) * 2.25F + (i == 0 ? 0.0F : debrisNoise(i, 10) * 2.5F);
            float localAge = age - delay;
            if (localAge <= 0.0F) continue;

            float grow = Mth.clamp(localAge / 3.0F, 0.0F, 1.0F);
            float fade = Mth.clamp(1.0F - localAge / (34.0F + debrisNoise(i, 11) * 24.0F), 0.0F, 1.0F);
            double angle = i * 2.399963229728653D + (debrisNoise(i, 12) - 0.5F) * 0.42F;
            float travel = 3.0F + localAge * (0.10F + debrisNoise(i, 13) * 0.13F);
            float centerX = (float) Math.cos(angle) * travel;
            float centerZ = (float) Math.sin(angle) * travel;
            float size = (3.0F + debrisNoise(i, 14) * 5.0F
                    + localAge * (0.12F + debrisNoise(i, 15) * 0.10F)) * grow;
            int red = 255;
            int green = switch (i % 3) {
                case 0 -> 232;
                case 1 -> 132;
                default -> 62;
            };
            int blue = switch (i % 3) {
                case 0 -> 170;
                case 1 -> 34;
                default -> 8;
            };
            appendTexturedQuad(builder, matrix, centerX, -98.45F, centerZ, size,
                    red, green, blue, Math.round(220.0F * fade * grow));
        }
        uploadIfPresent(builder);
    }

    private static void drawStagedDetonations(Matrix4f matrix, float age) {
        if (age >= ClientMoonEvents.RUPTURE_WAVE_TICK + 60.0F) return;
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        appendDetonationStage(builder, matrix, age, ClientMoonEvents.SECONDARY_BURST_TICK,
                7, 6.0F, 6.0F, 0.48F, 30.0F, 40, 255, 132, 34);
        appendDetonationStage(builder, matrix, age, ClientMoonEvents.CORE_REIGNITION_TICK,
                10, 11.0F, 10.0F, 0.78F, 44.0F, 50, 255, 205, 104);
        appendDetonationStage(builder, matrix, age, ClientMoonEvents.RUPTURE_WAVE_TICK,
                14, 17.0F, 14.0F, 1.08F, 60.0F, 60, 255, 72, 12);
        uploadIfPresent(builder);
    }

    private static void appendDetonationStage(BufferBuilder builder, Matrix4f matrix, float age, float start,
                                               int satellites, float orbitRadius, float baseSize, float growth,
                                               float duration, int salt, int red, int green, int blue) {
        float stageAge = age - start;
        if (stageAge < 0.0F || stageAge >= duration) return;

        float attack = Mth.clamp(stageAge / 1.5F, 0.0F, 1.0F);
        float decay = Mth.clamp(1.0F - stageAge / duration, 0.0F, 1.0F);
        float broadSize = (baseSize * 1.55F + stageAge * growth) * attack;
        appendTexturedQuad(builder, matrix, 0.0F, -98.35F, 0.0F, broadSize,
                red, green, blue, Math.round(220.0F * attack * decay));

        float coreLife = Mth.clamp(1.0F - stageAge / 9.0F, 0.0F, 1.0F) * attack;
        appendTexturedQuad(builder, matrix, 0.0F, -98.15F, 0.0F,
                (baseSize * 0.62F + stageAge * 1.22F) * attack,
                255, 250, 224, Math.round(255.0F * coreLife));

        for (int i = 0; i < satellites; i++) {
            float delay = i * 1.35F + debrisNoise(i, salt) * 2.0F;
            float localAge = stageAge - delay;
            if (localAge <= 0.0F) continue;
            float localAttack = Mth.clamp(localAge / 1.4F, 0.0F, 1.0F);
            float localDecay = Mth.clamp(1.0F - localAge / (duration * 0.68F), 0.0F, 1.0F);
            double angle = i * 2.399963229728653D + (debrisNoise(i, salt + 1) - 0.5F) * 0.58F;
            float travel = orbitRadius * (0.55F + debrisNoise(i, salt + 2) * 0.55F)
                    + localAge * (0.08F + debrisNoise(i, salt + 3) * 0.12F);
            float size = (baseSize * (0.38F + debrisNoise(i, salt + 4) * 0.34F)
                    + localAge * growth * (0.32F + debrisNoise(i, salt + 5) * 0.30F)) * localAttack;
            int satelliteGreen = Mth.clamp(green + i % 3 * 28, 0, 255);
            int satelliteBlue = Mth.clamp(blue + i % 2 * 36, 0, 255);
            appendTexturedQuad(builder, matrix,
                    (float) Math.cos(angle) * travel, -98.05F,
                    (float) Math.sin(angle) * travel, size,
                    red, satelliteGreen, satelliteBlue,
                    Math.round(235.0F * localAttack * localDecay));
        }
    }

    private static void drawSmokeBlooms(Matrix4f matrix, float age) {
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < 13; i++) {
            float delay = 8.0F + (i % 5) * 4.0F + (i == 0 ? 0.0F : debrisNoise(i, 20) * 3.0F);
            float localAge = age - delay;
            if (localAge <= 0.0F) continue;

            float grow = Mth.clamp(localAge / 9.0F, 0.0F, 1.0F);
            float fade = Mth.clamp(1.0F - localAge / (68.0F + debrisNoise(i, 21) * 30.0F), 0.0F, 1.0F);
            double angle = i * 2.399963229728653D + (debrisNoise(i, 22) - 0.5F) * 0.55F;
            float travel = 5.0F + localAge * (0.07F + debrisNoise(i, 23) * 0.09F);
            float size = (5.0F + debrisNoise(i, 24) * 7.0F + localAge * 0.11F) * grow;
            int shade = 38 + Math.round(debrisNoise(i, 25) * 24.0F);
            appendTexturedQuad(builder, matrix,
                    (float) Math.cos(angle) * travel, -97.75F,
                    (float) Math.sin(angle) * travel, size,
                    shade + 18, shade + 6, shade,
                    Math.round((42.0F + debrisNoise(i, 26) * 38.0F) * fade * grow));
        }
        appendStageSmoke(builder, matrix, age, ClientMoonEvents.CORE_REIGNITION_TICK + 5.0F, 7, 70);
        appendStageSmoke(builder, matrix, age, ClientMoonEvents.RUPTURE_WAVE_TICK + 5.0F, 10, 80);
        uploadIfPresent(builder);
    }

    private static void appendStageSmoke(BufferBuilder builder, Matrix4f matrix, float age,
                                         float start, int count, int salt) {
        for (int i = 0; i < count; i++) {
            float delay = i % 4 * 2.4F + debrisNoise(i, salt) * 2.2F;
            float localAge = age - start - delay;
            if (localAge <= 0.0F || localAge >= 66.0F) continue;
            float attack = Mth.clamp(localAge / 8.0F, 0.0F, 1.0F);
            float decay = Mth.clamp(1.0F - localAge / 66.0F, 0.0F, 1.0F);
            double angle = i * 2.399963229728653D + (debrisNoise(i, salt + 1) - 0.5F) * 0.7F;
            float travel = 7.0F + localAge * (0.09F + debrisNoise(i, salt + 2) * 0.10F);
            float size = (6.0F + debrisNoise(i, salt + 3) * 8.0F + localAge * 0.13F) * attack;
            int shade = 32 + Math.round(debrisNoise(i, salt + 4) * 22.0F);
            appendTexturedQuad(builder, matrix,
                    (float) Math.cos(angle) * travel, -97.65F,
                    (float) Math.sin(angle) * travel, size,
                    shade + 20, shade + 7, shade,
                    Math.round((48.0F + debrisNoise(i, salt + 5) * 40.0F) * attack * decay));
        }
    }

    private static void drawShockwaveAndSparks(Matrix4f matrix, float age, float life) {
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float radius = 20.0F + age * 1.45F;
        float width = 2.0F + 8.0F * life;
        int ringAlpha = Math.round(220.0F * life);
        ring(builder, matrix, radius, width, ringAlpha, 255, 156, 32);

        float hotLife = Mth.clamp(1.0F - age / 42.0F, 0.0F, 1.0F);
        ring(builder, matrix, 11.0F + age * 0.92F, 0.8F + hotLife * 2.4F,
                Math.round(245.0F * hotLife), 255, 242, 196);

        float delayedAge = age - 9.0F;
        if (delayedAge > 0.0F) {
            float delayedLife = Mth.clamp(1.0F - delayedAge / 96.0F, 0.0F, 1.0F)
                    * Mth.clamp(delayedAge / 4.0F, 0.0F, 1.0F);
            ring(builder, matrix, 16.0F + delayedAge * 1.16F, 1.3F + delayedLife * 4.5F,
                    Math.round(190.0F * delayedLife), 255, 70, 18);
        }

        stagedShockwave(builder, matrix, age, ClientMoonEvents.SECONDARY_BURST_TICK,
                1.72F, 34.0F, 225, 255, 188, 72);
        stagedShockwave(builder, matrix, age, ClientMoonEvents.CORE_REIGNITION_TICK,
                2.12F, 48.0F, 245, 255, 238, 190);
        stagedShockwave(builder, matrix, age, ClientMoonEvents.RUPTURE_WAVE_TICK,
                2.58F, 62.0F, 235, 255, 74, 15);
        stagedShockwave(builder, matrix, age, ClientMoonEvents.RUPTURE_WAVE_TICK + 5.0F,
                1.82F, 58.0F, 180, 255, 158, 42);

        float rayLife = Mth.clamp(1.0F - age / 44.0F, 0.0F, 1.0F);
        for (int i = 0; i < 20; i++) {
            double angle = i * 2.399963229728653D + (debrisNoise(i, 30) - 0.5F) * 0.32F;
            float inner = 5.0F + age * (0.20F + debrisNoise(i, 31) * 0.10F);
            float length = 18.0F + age * (0.62F + debrisNoise(i, 32) * 0.55F);
            float halfWidth = 0.7F + debrisNoise(i, 33) * 1.8F;
            ray(builder, matrix, angle, inner, length, halfWidth,
                    Math.round(205.0F * rayLife), i);
        }
        stagedEjectaRays(builder, matrix, age, ClientMoonEvents.SECONDARY_BURST_TICK, 12, 90);
        stagedEjectaRays(builder, matrix, age, ClientMoonEvents.CORE_REIGNITION_TICK, 18, 110);
        stagedEjectaRays(builder, matrix, age, ClientMoonEvents.RUPTURE_WAVE_TICK, 24, 140);

        int shardAlpha = Math.round(255.0F * life);
        for (int i = 0; i < 32; i++) {
            double angle = i * 2.399963229728653D + Math.sin(i * 4.17D) * 0.24D;
            float speed = 0.32F + (i % 7) * 0.085F;
            float inner = 9.0F + age * speed;
            float length = 4.0F + (i % 5) * 1.7F + age * 0.10F;
            float halfWidth = 0.45F + (i % 3) * 0.32F;
            shard(builder, matrix, angle, inner, length, halfWidth, shardAlpha, i);
        }

        float trailLife = Mth.clamp(age / 4.0F, 0.0F, 1.0F)
                * Mth.clamp(1.0F - age / 112.0F, 0.0F, 1.0F);
        if (trailLife > 0.0F) {
            for (int gridZ = 0; gridZ < MOON_FRAGMENT_GRID; gridZ++) {
                for (int gridX = 0; gridX < MOON_FRAGMENT_GRID; gridX++) {
                    int index = gridZ * MOON_FRAGMENT_GRID + gridX;
                    float baseX = -20.0F + (gridX + 0.5F) * 8.0F;
                    float baseZ = -20.0F + (gridZ + 0.5F) * 8.0F;
                    double angle = moonFragmentAngle(index, baseX, baseZ);
                    float directionX = (float) Math.cos(angle);
                    float directionZ = (float) Math.sin(angle);
                    float distance = moonFragmentDistance(index, age);
                    fragmentTrail(builder, matrix,
                            baseX + directionX * distance, baseZ + directionZ * distance,
                            directionX, directionZ, 3.0F + Math.min(age * 0.19F, 18.0F),
                            0.45F + debrisNoise(index, 34) * 0.65F,
                            Math.round(210.0F * trailLife));
                }
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void stagedShockwave(BufferBuilder builder, Matrix4f matrix, float age, float start,
                                        float speed, float duration, int maxAlpha,
                                        int red, int green, int blue) {
        float localAge = age - start;
        if (localAge < 0.0F || localAge >= duration) return;
        float attack = Mth.clamp(localAge / 2.0F, 0.0F, 1.0F);
        float decay = Mth.clamp(1.0F - localAge / duration, 0.0F, 1.0F);
        float strength = attack * decay;
        ring(builder, matrix, 7.0F + localAge * speed, 0.9F + strength * 4.2F,
                Math.round(maxAlpha * strength), red, green, blue);
    }

    private static void stagedEjectaRays(BufferBuilder builder, Matrix4f matrix, float age,
                                         float start, int count, int salt) {
        float localAge = age - start;
        if (localAge < 0.0F || localAge >= 34.0F) return;
        float attack = Mth.clamp(localAge / 1.5F, 0.0F, 1.0F);
        float decay = Mth.clamp(1.0F - localAge / 34.0F, 0.0F, 1.0F);
        for (int i = 0; i < count; i++) {
            double angle = i * 2.399963229728653D + (debrisNoise(i, salt) - 0.5F) * 0.48F;
            float inner = 3.0F + localAge * (0.28F + debrisNoise(i, salt + 1) * 0.16F);
            float length = 12.0F + localAge * (0.72F + debrisNoise(i, salt + 2) * 0.62F);
            float width = 0.55F + debrisNoise(i, salt + 3) * 1.55F;
            ray(builder, matrix, angle, inner, length, width,
                    Math.round(225.0F * attack * decay), i + salt);
        }
    }

    private static void ring(BufferBuilder builder, Matrix4f matrix, float radius, float width,
                             int alpha, int red, int green, int blue) {
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            ringVertex(builder, matrix, a0, radius - width, red, green, blue, alpha);
            ringVertex(builder, matrix, a1, radius - width, red, green, blue, alpha);
            ringVertex(builder, matrix, a1, radius + width, red, green, blue, 0);
            ringVertex(builder, matrix, a0, radius + width, red, green, blue, 0);
        }
    }

    private static void ray(BufferBuilder builder, Matrix4f matrix, double angle, float inner, float length,
                            float halfWidth, int alpha, int index) {
        float directionX = (float) Math.cos(angle);
        float directionZ = (float) Math.sin(angle);
        float perpendicularX = -directionZ * halfWidth;
        float perpendicularZ = directionX * halfWidth;
        float outer = inner + length;
        int green = 174 + index % 3 * 30;
        builder.addVertex(matrix, directionX * inner + perpendicularX, -98.0F,
                        directionZ * inner + perpendicularZ)
                .setColor(255, green, 92, alpha);
        builder.addVertex(matrix, directionX * outer + perpendicularX * 0.08F, -98.0F,
                        directionZ * outer + perpendicularZ * 0.08F)
                .setColor(255, 220, 144, 0);
        builder.addVertex(matrix, directionX * outer - perpendicularX * 0.08F, -98.0F,
                        directionZ * outer - perpendicularZ * 0.08F)
                .setColor(255, 220, 144, 0);
        builder.addVertex(matrix, directionX * inner - perpendicularX, -98.0F,
                        directionZ * inner - perpendicularZ)
                .setColor(255, green, 92, alpha);
    }

    private static void fragmentTrail(BufferBuilder builder, Matrix4f matrix,
                                      float headX, float headZ, float directionX, float directionZ,
                                      float length, float halfWidth, int alpha) {
        float perpendicularX = -directionZ * halfWidth;
        float perpendicularZ = directionX * halfWidth;
        float tailX = headX - directionX * length;
        float tailZ = headZ - directionZ * length;
        builder.addVertex(matrix, tailX + perpendicularX * 0.12F, -97.95F,
                        tailZ + perpendicularZ * 0.12F)
                .setColor(255, 52, 6, 0);
        builder.addVertex(matrix, headX + perpendicularX, -97.95F, headZ + perpendicularZ)
                .setColor(255, 178, 42, alpha);
        builder.addVertex(matrix, headX - perpendicularX, -97.95F, headZ - perpendicularZ)
                .setColor(255, 178, 42, alpha);
        builder.addVertex(matrix, tailX - perpendicularX * 0.12F, -97.95F,
                        tailZ - perpendicularZ * 0.12F)
                .setColor(255, 52, 6, 0);
    }

    private static void drawMoonFragments(ClientLevel level, Matrix4f matrix, float age) {
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int phase = level.getMoonPhase();
        float phaseU = (phase % 4) * 0.25F;
        float phaseV = ((phase / 4) % 2) * 0.5F;
        float fragmentFade = Mth.clamp((ClientMoonEvents.EXPLOSION_DURATION - age) / 45.0F, 0.0F, 1.0F);
        float heat = Mth.clamp((age - 16.0F) / 105.0F, 0.0F, 1.0F);
        int red = 255;
        int green = Math.round(255.0F - heat * 80.0F);
        int blue = Math.round(255.0F - heat * 155.0F);
        int alpha = Math.round(255.0F * fragmentFade);
        float cellSize = 40.0F / MOON_FRAGMENT_GRID;
        float halfSize = cellSize * 0.506F;

        for (int gridZ = 0; gridZ < MOON_FRAGMENT_GRID; gridZ++) {
            for (int gridX = 0; gridX < MOON_FRAGMENT_GRID; gridX++) {
                int index = gridZ * MOON_FRAGMENT_GRID + gridX;
                float baseX = -20.0F + (gridX + 0.5F) * cellSize;
                float baseZ = -20.0F + (gridZ + 0.5F) * cellSize;
                double angle = moonFragmentAngle(index, baseX, baseZ);
                float directionX = (float) Math.cos(angle);
                float directionZ = (float) Math.sin(angle);
                float distance = moonFragmentDistance(index, age);
                float centerX = baseX + directionX * distance;
                float centerZ = baseZ + directionZ * distance;
                float spinDirection = (index & 1) == 0 ? 1.0F : -1.0F;
                float spin = age * (1.5F + debrisNoise(index, 4) * 3.0F) * spinDirection * Mth.DEG_TO_RAD;
                float tumbleRate = 0.075F + debrisNoise(index, 5) * 0.035F;
                float tumble = 0.18F + 0.82F * Math.abs(Mth.cos(age * tumbleRate));
                float halfX = halfSize * tumble;
                float depth = -97.9F + (debrisNoise(index, 6) - 0.5F) * age * 0.035F;

                float uLeft = phaseU + 0.25F * (1.0F - gridX / (float) MOON_FRAGMENT_GRID);
                float uRight = phaseU + 0.25F * (1.0F - (gridX + 1.0F) / MOON_FRAGMENT_GRID);
                float vLow = phaseV + 0.5F * gridZ / MOON_FRAGMENT_GRID;
                float vHigh = phaseV + 0.5F * (gridZ + 1.0F) / MOON_FRAGMENT_GRID;

                moonFragmentVertex(builder, matrix, centerX, depth, centerZ, -halfX, -halfSize,
                        spin, uLeft, vLow, red, green, blue, alpha);
                moonFragmentVertex(builder, matrix, centerX, depth, centerZ, halfX, -halfSize,
                        spin, uRight, vLow, red, green, blue, alpha);
                moonFragmentVertex(builder, matrix, centerX, depth, centerZ, halfX, halfSize,
                        spin, uRight, vHigh, red, green, blue, alpha);
                moonFragmentVertex(builder, matrix, centerX, depth, centerZ, -halfX, halfSize,
                        spin, uLeft, vHigh, red, green, blue, alpha);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static double moonFragmentAngle(int index, float baseX, float baseZ) {
        float radialLength = Mth.sqrt(baseX * baseX + baseZ * baseZ);
        return radialLength < 0.01F
                ? debrisNoise(index, 0) * Mth.TWO_PI
                : Math.atan2(baseZ, baseX) + (debrisNoise(index, 1) - 0.5F) * 0.34F;
    }

    private static float moonFragmentDistance(int index, float age) {
        float speed = 0.30F + debrisNoise(index, 2) * 0.30F;
        float acceleration = 0.0008F + debrisNoise(index, 3) * 0.0008F;
        return Mth.sqrt(age) * 1.4F + age * speed + age * age * acceleration;
    }

    private static void moonFragmentVertex(BufferBuilder builder, Matrix4f matrix,
                                           float centerX, float y, float centerZ,
                                           float localX, float localZ, float spin,
                                           float u, float v, int red, int green, int blue, int alpha) {
        float cosine = Mth.cos(spin);
        float sine = Mth.sin(spin);
        float rotatedX = localX * cosine - localZ * sine;
        float rotatedZ = localX * sine + localZ * cosine;
        builder.addVertex(matrix, centerX + rotatedX, y, centerZ + rotatedZ)
                .setUv(u, v)
                .setColor(red, green, blue, alpha);
    }

    private static float debrisNoise(int index, int salt) {
        double value = Math.sin(index * 12.9898D + salt * 78.233D) * 43758.5453D;
        return (float) (value - Math.floor(value));
    }

    private static void uploadIfPresent(BufferBuilder builder) {
        var mesh = builder.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static void ringVertex(BufferBuilder builder, Matrix4f matrix, double angle, float radius,
                                   int red, int green, int blue, int alpha) {
        builder.addVertex(matrix, (float) Math.cos(angle) * radius, -98.3F,
                        (float) Math.sin(angle) * radius)
                .setColor(red, green, blue, alpha);
    }

    private static void shard(BufferBuilder builder, Matrix4f matrix, double angle, float inner, float length,
                              float halfWidth, int alpha, int index) {
        float dx = (float) Math.cos(angle);
        float dz = (float) Math.sin(angle);
        float px = -dz * halfWidth;
        float pz = dx * halfWidth;
        float x0 = dx * inner;
        float z0 = dz * inner;
        float x1 = dx * (inner + length);
        float z1 = dz * (inner + length);
        int green = 90 + index % 4 * 38;
        builder.addVertex(matrix, x0 + px, -98.1F, z0 + pz).setColor(255, green, 12, alpha);
        builder.addVertex(matrix, x1 + px * 0.25F, -98.1F, z1 + pz * 0.25F).setColor(255, 214, 88, 0);
        builder.addVertex(matrix, x1 - px * 0.25F, -98.1F, z1 - pz * 0.25F).setColor(255, 214, 88, 0);
        builder.addVertex(matrix, x0 - px, -98.1F, z0 - pz).setColor(255, green, 12, alpha);
    }

    private static void orientCelestialPlane(ClientLevel level, float partialTick, PoseStack poses) {
        poses.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partialTick) * 360.0F));
    }

    private static void drawTexturedQuad(Matrix4f matrix, float centerX, float y, float centerZ, float size,
                                         int red, int green, int blue, int alpha) {
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        appendTexturedQuad(builder, matrix, centerX, y, centerZ, size, red, green, blue, alpha);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void appendTexturedQuad(BufferBuilder builder, Matrix4f matrix,
                                           float centerX, float y, float centerZ, float size,
                                           int red, int green, int blue, int alpha) {
        builder.addVertex(matrix, centerX - size, y, centerZ - size).setUv(0.0F, 0.0F)
                .setColor(red, green, blue, alpha);
        builder.addVertex(matrix, centerX + size, y, centerZ - size).setUv(1.0F, 0.0F)
                .setColor(red, green, blue, alpha);
        builder.addVertex(matrix, centerX + size, y, centerZ + size).setUv(1.0F, 1.0F)
                .setColor(red, green, blue, alpha);
        builder.addVertex(matrix, centerX - size, y, centerZ + size).setUv(0.0F, 1.0F)
                .setColor(red, green, blue, alpha);
    }
}
