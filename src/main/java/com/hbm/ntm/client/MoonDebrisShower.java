package com.hbm.ntm.client;

import com.hbm.ntm.network.MoonDebrisImpactPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Player-local debris visuals whose loaded impacts request tightly validated server craters. */
public final class MoonDebrisShower {
    private static final ResourceLocation MOON_ROCK = ResourceLocation.withDefaultNamespace(
            "textures/block/tuff.png");
    private static final ResourceLocation FLASH = ResourceLocation.withDefaultNamespace(
            "textures/particle/flash.png");
    private static final RenderType ROCK_TYPE = RenderType.entityCutoutNoCull(MOON_ROCK, false);
    private static final RenderType GLOW_TYPE = RenderType.entityTranslucentEmissive(FLASH, false);
    private static final double PHI = (1.0D + Math.sqrt(5.0D)) * 0.5D;
    private static final Vec3[] ROCK_VERTICES = {
            normalizedRockVertex(-1.0D, PHI, 0.0D), normalizedRockVertex(1.0D, PHI, 0.0D),
            normalizedRockVertex(-1.0D, -PHI, 0.0D), normalizedRockVertex(1.0D, -PHI, 0.0D),
            normalizedRockVertex(0.0D, -1.0D, PHI), normalizedRockVertex(0.0D, 1.0D, PHI),
            normalizedRockVertex(0.0D, -1.0D, -PHI), normalizedRockVertex(0.0D, 1.0D, -PHI),
            normalizedRockVertex(PHI, 0.0D, -1.0D), normalizedRockVertex(PHI, 0.0D, 1.0D),
            normalizedRockVertex(-PHI, 0.0D, -1.0D), normalizedRockVertex(-PHI, 0.0D, 1.0D)
    };
    private static final int[][] ROCK_FACES = {
            {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
            {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
            {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
            {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
    };
    private static final int SHOWER_START = 44;
    private static final int SHOWER_END = 260;
    private static final int MAX_ACTIVE_DEBRIS = 68;
    private static final List<Debris> DEBRIS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private static boolean active;
    private static int showerAge;
    private static int nextId;

    private MoonDebrisShower() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MoonDebrisShower::tick);
        NeoForge.EVENT_BUS.addListener(MoonDebrisShower::render);
    }

    static void begin() {
        reset();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || minecraft.level.dimension() != Level.OVERWORLD) return;
        active = true;
        long seed = minecraft.level.getGameTime()
                ^ minecraft.player.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(minecraft.player.getUUID().getLeastSignificantBits(), 23);
        RANDOM.setSeed(seed);
    }

    static void reset() {
        active = false;
        showerAge = 0;
        nextId = 0;
        DEBRIS.clear();
    }

    private static void tick(ClientTickEvent.Post event) {
        if (!active) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || level.dimension() != Level.OVERWORLD) {
            reset();
            return;
        }

        showerAge++;
        int spawnCount = 0;
        if (showerAge == SHOWER_START) spawnCount += 9;
        if (showerAge == ClientMoonEvents.RUPTURE_WAVE_TICK) spawnCount += 13;
        if (showerAge == 82) spawnCount += 8;
        if (showerAge == 108) spawnCount += 7;
        if (showerAge == 142) spawnCount += 8;
        if (showerAge == 184) spawnCount += 7;
        if (showerAge == 226) spawnCount += 5;
        if (showerAge >= 48 && showerAge <= SHOWER_END && showerAge % 2 == 0) spawnCount++;
        if (showerAge >= 60 && showerAge <= 220 && showerAge % 5 == 0) spawnCount++;
        spawnCount = Math.min(spawnCount, MAX_ACTIVE_DEBRIS - DEBRIS.size());
        for (int i = 0; i < spawnCount; i++) spawn(minecraft);

        Iterator<Debris> iterator = DEBRIS.iterator();
        while (iterator.hasNext()) {
            Debris debris = iterator.next();
            debris.tick();
            emitTrailParticles(level, debris);
            boolean hitGround = debris.hitGround(level);
            if (hitGround || debris.age >= debris.maxAge
                    || debris.y < level.getMinBuildHeight() - 20.0D) {
                if (hitGround) impact(level, debris);
                iterator.remove();
            }
        }

        if (showerAge > 560 && DEBRIS.isEmpty()) active = false;
    }

    private static void spawn(Minecraft minecraft) {
        double renderRadius = Mth.clamp(minecraft.options.getEffectiveRenderDistance() * 16.0D * 0.70D,
                36.0D, 160.0D);
        double viewAngle = Math.atan2(Mth.cos(minecraft.player.getYRot() * Mth.DEG_TO_RAD),
                -Mth.sin(minecraft.player.getYRot() * Mth.DEG_TO_RAD));
        double angle = RANDOM.nextDouble() < 0.78D
                ? viewAngle + (RANDOM.nextDouble() - 0.5D) * Math.PI * 0.92D
                : RANDOM.nextDouble() * Mth.TWO_PI;
        double distance = 16.0D + RANDOM.nextDouble() * Math.max(1.0D, renderRadius - 16.0D);
        double x = minecraft.player.getX() + Math.cos(angle) * distance;
        double z = minecraft.player.getZ() + Math.sin(angle) * distance;
        double y = minecraft.player.getY() + 76.0D + RANDOM.nextDouble() * 74.0D;
        float sizeRoll = RANDOM.nextFloat();
        float size = sizeRoll < 0.18F
                ? 11.0F + RANDOM.nextFloat() * 8.0F
                : sizeRoll < 0.50F
                ? 6.5F + RANDOM.nextFloat() * 6.5F
                : sizeRoll < 0.85F
                ? 3.0F + RANDOM.nextFloat() * 4.5F
                : 1.2F + RANDOM.nextFloat() * 2.2F;
        double driftScale = Mth.clamp(1.12D - size * 0.034D, 0.46D, 1.0D);
        double inward = (0.10D + RANDOM.nextDouble() * 0.30D) * driftScale;
        double tangent = (RANDOM.nextDouble() - 0.5D) * 0.24D * driftScale;
        double velocityX = -Math.cos(angle) * inward - Math.sin(angle) * tangent;
        double velocityZ = -Math.sin(angle) * inward + Math.cos(angle) * tangent;
        double velocityY = -(0.30D + RANDOM.nextDouble() * 0.38D) * driftScale;
        float aspectX;
        float aspectY;
        float aspectZ;
        switch (RANDOM.nextInt(4)) {
            case 1 -> { // Flat slabs torn from the moon's crust.
                aspectX = 1.18F + RANDOM.nextFloat() * 0.52F;
                aspectY = 0.34F + RANDOM.nextFloat() * 0.30F;
                aspectZ = 0.82F + RANDOM.nextFloat() * 0.44F;
            }
            case 2 -> { // Long, tumbling splinters.
                aspectX = 0.38F + RANDOM.nextFloat() * 0.34F;
                aspectY = 1.30F + RANDOM.nextFloat() * 0.68F;
                aspectZ = 0.52F + RANDOM.nextFloat() * 0.42F;
            }
            case 3 -> { // Broad asymmetric chunks.
                aspectX = 1.30F + RANDOM.nextFloat() * 0.48F;
                aspectY = 0.70F + RANDOM.nextFloat() * 0.48F;
                aspectZ = 0.48F + RANDOM.nextFloat() * 0.34F;
            }
            default -> { // Rough boulders.
                aspectX = 0.72F + RANDOM.nextFloat() * 0.46F;
                aspectY = 0.72F + RANDOM.nextFloat() * 0.46F;
                aspectZ = 0.72F + RANDOM.nextFloat() * 0.46F;
            }
        }
        float spinScale = Mth.clamp(3.2F / Mth.sqrt(Math.max(size, 1.0F)), 0.55F, 2.6F);
        DEBRIS.add(new Debris(nextId++, x, y, z, velocityX, velocityY, velocityZ, size,
                RANDOM.nextFloat() * 360.0F, RANDOM.nextFloat() * 360.0F, RANDOM.nextFloat() * 360.0F,
                (RANDOM.nextFloat() - 0.5F) * spinScale * 2.0F,
                (RANDOM.nextFloat() - 0.5F) * spinScale * 2.4F,
                (RANDOM.nextFloat() - 0.5F) * spinScale * 2.0F,
                280 + RANDOM.nextInt(181) + Math.round(size * 5.0F),
                aspectX, aspectY, aspectZ,
                RANDOM.nextLong()));
    }

    private static void emitTrailParticles(ClientLevel level, Debris debris) {
        if ((debris.age & 1) == 0) {
            level.addParticle(ParticleTypes.FLAME,
                    debris.x - debris.velocityX * 1.8D,
                    debris.y - debris.velocityY * 1.8D,
                    debris.z - debris.velocityZ * 1.8D,
                    -debris.velocityX * 0.04D, 0.035D, -debris.velocityZ * 0.04D);
        }
        if (debris.age % 4 == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    debris.x - debris.velocityX * 2.4D,
                    debris.y - debris.velocityY * 2.4D,
                    debris.z - debris.velocityZ * 2.4D,
                    0.0D, 0.025D, 0.0D);
        }
    }

    private static void impact(ClientLevel level, Debris debris) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(debris.x), Mth.floor(debris.z));
        double impactY = ground + 0.25D;
        PacketDistributor.sendToServer(MoonDebrisImpactPayload.at(
                Mth.floor(debris.x), Mth.floor(debris.z), debris.craterScale()));
        if (debris.size >= 4.2F) {
            level.addParticle(ParticleTypes.EXPLOSION_EMITTER, debris.x, impactY, debris.z, 0.0D, 0.0D, 0.0D);
        } else {
            level.addParticle(ParticleTypes.EXPLOSION, debris.x, impactY, debris.z, 0.0D, 0.0D, 0.0D);
        }
        int particles = 12 + Math.round(debris.size * 3.0F);
        for (int i = 0; i < particles; i++) {
            double angle = RANDOM.nextDouble() * Mth.TWO_PI;
            double speed = 0.08D + RANDOM.nextDouble() * 0.34D;
            double velocityX = Math.cos(angle) * speed;
            double velocityZ = Math.sin(angle) * speed;
            double velocityY = 0.08D + RANDOM.nextDouble() * 0.42D;
            level.addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME,
                    debris.x, impactY, debris.z, velocityX, velocityY, velocityZ);
            if (i % 5 == 0) {
                level.addParticle(ParticleTypes.LAVA, debris.x, impactY, debris.z,
                        velocityX * 0.6D, velocityY, velocityZ * 0.6D);
            }
        }
        level.playLocalSound(debris.x, impactY, debris.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.AMBIENT, Mth.clamp(debris.size * 0.55F, 1.4F, 4.0F),
                0.42F + RANDOM.nextFloat() * 0.18F, false);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || DEBRIS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        Vec3 left = new Vec3(event.getCamera().getLeftVector()).normalize();
        Vec3 up = new Vec3(event.getCamera().getUpVector()).normalize();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        // BufferSource may finish its current non-fixed BufferBuilder when another render type
        // is requested. Complete the entire opaque moon-rock pass before acquiring the emissive
        // consumer; retaining both consumers at once leaves the first one in a non-building state.
        VertexConsumer rock = buffers.getBuffer(ROCK_TYPE);
        for (Debris debris : DEBRIS) {
            Vec3 position = debris.interpolatedPosition(partialTick);
            double cullRadius = debris.size * 2.5D;
            if (!event.getFrustum().isVisible(AABB.ofSize(position,
                    cullRadius, cullRadius, cullRadius))) continue;

            poses.pushPose();
            poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            poses.mulPose(Axis.XP.rotationDegrees(debris.rotationX + debris.spinX * partialTick));
            poses.mulPose(Axis.YP.rotationDegrees(debris.rotationY + debris.spinY * partialTick));
            poses.mulPose(Axis.ZP.rotationDegrees(debris.rotationZ + debris.spinZ * partialTick));
            poses.scale(debris.size * debris.aspectX, debris.size * debris.aspectY, debris.size * debris.aspectZ);
            PoseStack.Pose pose = poses.last();
            float cooling = Mth.clamp(debris.age / (float) debris.maxAge, 0.0F, 1.0F);
            float red = (0.92F - cooling * 0.18F) * debris.surfaceShade;
            float green = (0.86F - cooling * 0.24F) * debris.surfaceShade;
            float blue = (0.82F - cooling * 0.22F) * debris.surfaceShade;
            renderRock(rock, pose, debris, red, green, blue);
            poses.popPose();
        }
        buffers.endBatch(ROCK_TYPE);

        VertexConsumer glow = buffers.getBuffer(GLOW_TYPE);
        for (Debris debris : DEBRIS) {
            Vec3 position = debris.interpolatedPosition(partialTick);
            double cullRadius = debris.size * 2.5D;
            if (!event.getFrustum().isVisible(AABB.ofSize(position,
                    cullRadius, cullRadius, cullRadius))) continue;
            renderTrail(poses.last(), glow, position.subtract(camera), debris, left, up);

            poses.pushPose();
            poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            poses.mulPose(Axis.XP.rotationDegrees(debris.rotationX + debris.spinX * partialTick));
            poses.mulPose(Axis.YP.rotationDegrees(debris.rotationY + debris.spinY * partialTick));
            poses.mulPose(Axis.ZP.rotationDegrees(debris.rotationZ + debris.spinZ * partialTick));
            poses.scale(debris.size * debris.aspectX, debris.size * debris.aspectY, debris.size * debris.aspectZ);
            float cooling = Mth.clamp(debris.age / (float) debris.maxAge, 0.0F, 1.0F);
            float pulse = 0.82F + Mth.sin((debris.age + partialTick) * 0.72F + debris.heatPhase) * 0.18F;
            renderHotCracks(glow, poses.last(), debris, (1.0F - cooling * 0.72F) * pulse);
            poses.popPose();
        }
        buffers.endBatch(GLOW_TYPE);
    }

    private static Vec3 normalizedRockVertex(double x, double y, double z) {
        return new Vec3(x, y, z).normalize().scale(0.54D);
    }

    private static void renderRock(VertexConsumer consumer, PoseStack.Pose pose, Debris debris,
                                   float red, float green, float blue) {
        for (int faceIndex = 0; faceIndex < ROCK_FACES.length; faceIndex++) {
            int[] face = ROCK_FACES[faceIndex];
            float shade = debris.faceShade[faceIndex];
            triangle(consumer, pose,
                    debris.rockVertex(face[0]), debris.rockVertex(face[1]), debris.rockVertex(face[2]),
                    red * shade, green * shade, blue * shade, 1.0F);
        }
    }

    private static void renderHotCracks(VertexConsumer consumer, PoseStack.Pose pose,
                                        Debris debris, float heat) {
        for (int crackIndex = 0; crackIndex < debris.hotFaces.length; crackIndex++) {
            int[] face = ROCK_FACES[debris.hotFaces[crackIndex]];
            Vec3 a = debris.rockVertex(face[0]);
            Vec3 b = debris.rockVertex(face[1]);
            Vec3 c = debris.rockVertex(face[2]);
            Vec3 center = a.add(b).add(c).scale(1.0D / 3.0D);
            Vec3 normal = outwardNormal(a, b, c, center);
            double bend = 0.16D + crackIndex * 0.035D;
            Vec3 start = center.lerp(a, 0.66D);
            Vec3 middle = center.lerp(b, bend);
            Vec3 end = center.lerp(c, 0.62D);
            float width = debris.crackWidth * (1.0F - crackIndex * 0.08F);
            float green = 0.20F + heat * 0.34F;
            float alpha = (0.48F + heat * 0.40F) * (1.0F - crackIndex * 0.07F);
            crackStrip(consumer, pose, start, middle, normal, width,
                    1.0F, green, 0.035F, alpha);
            crackStrip(consumer, pose, middle, end, normal, width * 0.72F,
                    1.0F, green * 0.82F, 0.025F, alpha * 0.92F);
        }
    }

    private static void crackStrip(VertexConsumer consumer, PoseStack.Pose pose,
                                   Vec3 start, Vec3 end, Vec3 normal, float width,
                                   float red, float green, float blue, float alpha) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-8D) return;
        Vec3 side = normal.cross(direction).normalize().scale(width);
        Vec3 lift = normal.scale(0.012D);
        Vec3 a = start.add(side).add(lift);
        Vec3 b = start.subtract(side).add(lift);
        Vec3 c = end.subtract(side.scale(0.62D)).add(lift);
        Vec3 d = end.add(side.scale(0.62D)).add(lift);
        face(consumer, pose,
                (float) a.x, (float) a.y, (float) a.z,
                (float) b.x, (float) b.y, (float) b.z,
                (float) c.x, (float) c.y, (float) c.z,
                (float) d.x, (float) d.y, (float) d.z,
                0.0F, 0.2F, 1.0F, 0.8F, red, green, blue, alpha,
                (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void triangle(VertexConsumer consumer, PoseStack.Pose pose,
                                 Vec3 a, Vec3 b, Vec3 c,
                                 float red, float green, float blue, float alpha) {
        Vec3 center = a.add(b).add(c).scale(1.0D / 3.0D);
        Vec3 normal = outwardNormal(a, b, c, center);
        face(consumer, pose,
                (float) a.x, (float) a.y, (float) a.z,
                (float) b.x, (float) b.y, (float) b.z,
                (float) c.x, (float) c.y, (float) c.z,
                (float) c.x, (float) c.y, (float) c.z,
                0.04F, 0.04F, 0.96F, 0.96F, red, green, blue, alpha,
                (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static Vec3 outwardNormal(Vec3 a, Vec3 b, Vec3 c, Vec3 center) {
        Vec3 normal = b.subtract(a).cross(c.subtract(a)).normalize();
        return normal.dot(center) < 0.0D ? normal.reverse() : normal;
    }

    private static void renderTrail(PoseStack.Pose pose, VertexConsumer glow, Vec3 head,
                                    Debris debris, Vec3 left, Vec3 up) {
        Vec3 reverse = new Vec3(debris.velocityX, debris.velocityY, debris.velocityZ).normalize().reverse();
        for (int i = 0; i < 6; i++) {
            float progress = i / 6.0F;
            Vec3 center = head.add(reverse.scale((i + 1.0D) * debris.size * 0.62D));
            float size = debris.size * (0.42F - progress * 0.27F);
            float alpha = (1.0F - progress) * 0.46F;
            billboard(glow, pose, center, left, up, size, 1.0F,
                    0.20F + progress * 0.16F, 0.025F, alpha);
        }
        billboard(glow, pose, head, left, up, debris.size * 0.48F,
                1.0F, 0.38F, 0.07F, 0.44F);
    }

    private static void billboard(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center,
                                  Vec3 left, Vec3 up, float halfSize,
                                  float red, float green, float blue, float alpha) {
        Vec3 horizontal = left.scale(halfSize);
        Vec3 vertical = up.scale(halfSize);
        texturedVertex(consumer, pose, center.add(horizontal).add(vertical), 0.0F, 0.0F,
                red, green, blue, alpha, 0.0F, 1.0F, 0.0F);
        texturedVertex(consumer, pose, center.subtract(horizontal).add(vertical), 1.0F, 0.0F,
                red, green, blue, alpha, 0.0F, 1.0F, 0.0F);
        texturedVertex(consumer, pose, center.subtract(horizontal).subtract(vertical), 1.0F, 1.0F,
                red, green, blue, alpha, 0.0F, 1.0F, 0.0F);
        texturedVertex(consumer, pose, center.add(horizontal).subtract(vertical), 0.0F, 1.0F,
                red, green, blue, alpha, 0.0F, 1.0F, 0.0F);
    }

    private static void cube(VertexConsumer consumer, PoseStack.Pose pose,
                             float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                             float u0, float v0, float u1, float v1,
                             float red, float green, float blue, float alpha) {
        face(consumer, pose, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ,
                u0, v0, u1, v1, red, green, blue, alpha, 0.0F, 1.0F, 0.0F);
        face(consumer, pose, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ,
                u0, v0, u1, v1, red, green, blue, alpha, 0.0F, -1.0F, 0.0F);
        face(consumer, pose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ,
                u0, v0, u1, v1, red, green, blue, alpha, -1.0F, 0.0F, 0.0F);
        face(consumer, pose, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ,
                u0, v0, u1, v1, red, green, blue, alpha, 1.0F, 0.0F, 0.0F);
        face(consumer, pose, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ,
                u0, v0, u1, v1, red, green, blue, alpha, 0.0F, 0.0F, -1.0F);
        face(consumer, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ,
                u0, v0, u1, v1, red, green, blue, alpha, 0.0F, 0.0F, 1.0F);
    }

    private static void face(VertexConsumer consumer, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float u0, float v0, float u1, float v1,
                             float red, float green, float blue, float alpha,
                             float normalX, float normalY, float normalZ) {
        texturedVertex(consumer, pose, new Vec3(x1, y1, z1), u0, v0,
                red, green, blue, alpha, normalX, normalY, normalZ);
        texturedVertex(consumer, pose, new Vec3(x2, y2, z2), u0, v1,
                red, green, blue, alpha, normalX, normalY, normalZ);
        texturedVertex(consumer, pose, new Vec3(x3, y3, z3), u1, v1,
                red, green, blue, alpha, normalX, normalY, normalZ);
        texturedVertex(consumer, pose, new Vec3(x4, y4, z4), u1, v0,
                red, green, blue, alpha, normalX, normalY, normalZ);
    }

    private static void texturedVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 position,
                                       float u, float v, float red, float green, float blue, float alpha,
                                       float normalX, float normalY, float normalZ) {
        consumer.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setColor(red, green, blue, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normalX, normalY, normalZ);
    }

    private static final class Debris {
        private final int id;
        private final float size;
        private final float spinX;
        private final float spinY;
        private final float spinZ;
        private final int maxAge;
        private final float aspectX;
        private final float aspectY;
        private final float aspectZ;
        private final double gravity;
        private final double terminalVelocity;
        private final float[] vertexRadius = new float[ROCK_VERTICES.length];
        private final float[] faceShade = new float[ROCK_FACES.length];
        private final int[] hotFaces = new int[4];
        private final float surfaceShade;
        private final float crackWidth;
        private final float heatPhase;
        private double previousX;
        private double previousY;
        private double previousZ;
        private double x;
        private double y;
        private double z;
        private double velocityX;
        private double velocityY;
        private double velocityZ;
        private float rotationX;
        private float rotationY;
        private float rotationZ;
        private int age;

        private Debris(int id, double x, double y, double z,
                       double velocityX, double velocityY, double velocityZ, float size,
                       float rotationX, float rotationY, float rotationZ,
                       float spinX, float spinY, float spinZ, int maxAge,
                       float aspectX, float aspectY, float aspectZ, long shapeSeed) {
            this.id = id;
            this.x = this.previousX = x;
            this.y = this.previousY = y;
            this.z = this.previousZ = z;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
            this.size = size;
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.spinX = spinX;
            this.spinY = spinY;
            this.spinZ = spinZ;
            this.maxAge = maxAge;
            this.aspectX = aspectX;
            this.aspectY = aspectY;
            this.aspectZ = aspectZ;
            this.gravity = Mth.clamp(0.0115D - size * 0.00038D, 0.0038D, 0.0105D);
            this.terminalVelocity = -Mth.clamp(1.02D - size * 0.021D, 0.52D, 0.98D);
            Random shape = new Random(shapeSeed);
            for (int i = 0; i < vertexRadius.length; i++) {
                vertexRadius[i] = 0.76F + shape.nextFloat() * 0.36F;
            }
            for (int i = 0; i < faceShade.length; i++) {
                faceShade[i] = 0.86F + shape.nextFloat() * 0.18F;
            }
            for (int i = 0; i < hotFaces.length; i++) {
                int candidate;
                boolean duplicate;
                do {
                    candidate = shape.nextInt(ROCK_FACES.length);
                    duplicate = false;
                    for (int previous = 0; previous < i; previous++) {
                        if (hotFaces[previous] == candidate) {
                            duplicate = true;
                            break;
                        }
                    }
                } while (duplicate);
                hotFaces[i] = candidate;
            }
            surfaceShade = 0.90F + shape.nextFloat() * 0.08F;
            crackWidth = 0.018F + shape.nextFloat() * 0.014F;
            heatPhase = shape.nextFloat() * Mth.TWO_PI;
        }

        private Vec3 rockVertex(int index) {
            return ROCK_VERTICES[index].scale(vertexRadius[index]);
        }

        private float craterScale() {
            float footprint = Math.max(aspectX, aspectZ);
            return Mth.clamp(size * footprint, 0.8F, 8.0F);
        }

        private void tick() {
            previousX = x;
            previousY = y;
            previousZ = z;
            x += velocityX;
            y += velocityY;
            z += velocityZ;
            velocityX *= 0.9985D;
            velocityZ *= 0.9985D;
            velocityY = Math.max(velocityY - gravity, terminalVelocity);
            rotationX += spinX;
            rotationY += spinY;
            rotationZ += spinZ;
            age++;
        }

        private boolean hitGround(ClientLevel level) {
            BlockPos column = new BlockPos(Mth.floor(x),
                    Mth.clamp(Mth.floor(y), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1),
                    Mth.floor(z));
            if (!level.isLoaded(column)) return false;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    column.getX(), column.getZ());
            return y - size * aspectY * 0.5D <= ground + 0.25D;
        }

        private Vec3 interpolatedPosition(float partialTick) {
            return new Vec3(Mth.lerp(partialTick, previousX, x),
                    Mth.lerp(partialTick, previousY, y), Mth.lerp(partialTick, previousZ, z));
        }
    }
}
