package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.FensuBlock;
import com.hbm.ntm.blockentity.FensuBlockEntity;
import com.hbm.ntm.client.sound.FensuSoundInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/** Exact grouped FEnSU OBJ with world-only wheel, plasma, trail, zap, and loop-sound animation. */
public final class FensuRenderer implements BlockEntityRenderer<FensuBlockEntity> {
    public static final ModelResourceLocation BASE = model("base");
    public static final ModelResourceLocation WHEEL = model("wheel");
    public static final ModelResourceLocation LIGHTS = model("lights");
    public static final ModelResourceLocation PLASMA = model("plasma");
    public static final ModelResourceLocation PLASMA_SPARKLE = model("plasma_sparkle");
    private final Map<FensuBlockEntity, FensuSoundInstance> sounds = new WeakHashMap<>();

    public FensuRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(FensuBlockEntity fensu, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        sounds.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (fensu.speed() > 0F && !sounds.containsKey(fensu)
                && Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.distanceToSqr(
                fensu.getBlockPos().getX() + 0.5D,
                fensu.getBlockPos().getY() + 5.5D,
                fensu.getBlockPos().getZ() + 0.5D) < 30D * 30D) {
            FensuSoundInstance sound = new FensuSoundInstance(fensu);
            sounds.put(fensu, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }

        poses.pushPose();
        poses.translate(0.5D, 0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(facingRotation(
                fensu.getBlockState().getValue(FensuBlock.FACING))));
        ThermalModelRenderer.render(BASE, poses, buffers, packedLight, packedOverlay);

        float speed = fensu.speed();
        poses.pushPose();
        poses.translate(0D, 5.5D, 0D);
        poses.mulPose(Axis.XP.rotationDegrees(fensu.rotation(partialTick)));
        poses.translate(0D, -5.5D, 0D);
        ThermalModelRenderer.render(WHEEL, poses, buffers, packedLight, packedOverlay);
        ThermalModelRenderer.render(LIGHTS, poses, buffers, LightTexture.FULL_BRIGHT, packedOverlay);
        if (speed > 0F) {
            poses.translate(0D, 5.5D, 0D);
            renderTrails(poses, buffers, speed);
            poses.translate(0D, -5.5D, 0D);
            ThermalModelRenderer.render(PLASMA, poses, buffers, LightTexture.FULL_BRIGHT, packedOverlay);
            if (Minecraft.getInstance().player == null
                    || Minecraft.getInstance().player.distanceToSqr(
                    fensu.getBlockPos().getX() + 0.5D,
                    fensu.getBlockPos().getY() + 5.5D,
                    fensu.getBlockPos().getZ() + 0.5D) < 100D * 100D) {
                ThermalModelRenderer.render(PLASMA_SPARKLE, poses, buffers,
                        LightTexture.FULL_BRIGHT, packedOverlay);
            }
        }
        poses.popPose();

        if (speed > 0F) renderZaps(fensu, poses, buffers);
        poses.popPose();
    }

    public static void renderStatic(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        ThermalModelRenderer.render(BASE, poses, buffers, light, overlay);
        ThermalModelRenderer.render(WHEEL, poses, buffers, light, overlay);
        ThermalModelRenderer.render(LIGHTS, poses, buffers, LightTexture.FULL_BRIGHT, overlay);
    }

    private static void renderTrails(PoseStack poses, MultiBufferSource buffers, float speed) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = poses.last();
        double span = speed * 0.75D;
        for (int side = -1; side <= 1; side += 2) {
            float x = 0.8125F * side;
            for (int spoke = 0; spoke < 8; spoke++) {
                double angle = Math.toRadians(spoke * 45D);
                trailSegment(consumer, pose, x, angle, angle + Math.toRadians(span), 0.75F, 0.5F);
                angle += Math.toRadians(span);
                trailSegment(consumer, pose, x, angle, angle + Math.toRadians(span), 0.5F, 0.25F);
                angle += Math.toRadians(span);
                trailSegment(consumer, pose, x, angle, angle + Math.toRadians(span), 0.25F, 0F);
            }
        }
    }

    private static void trailSegment(VertexConsumer consumer, PoseStack.Pose pose, float x,
                                     double start, double end, float startAlpha, float endAlpha) {
        float inner = 4.125F;
        float outer = 4.375F;
        gradientVertex(consumer, pose, x, (float) Math.cos(start) * inner,
                (float) Math.sin(start) * inner, startAlpha);
        gradientVertex(consumer, pose, x, (float) Math.cos(start) * outer,
                (float) Math.sin(start) * outer, startAlpha);
        gradientVertex(consumer, pose, x, (float) Math.cos(end) * outer,
                (float) Math.sin(end) * outer, endAlpha);
        gradientVertex(consumer, pose, x, (float) Math.cos(end) * inner,
                (float) Math.sin(end) * inner, endAlpha);
    }

    private static void gradientVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                       float x, float y, float z, float alpha) {
        consumer.addVertex(pose, x, y, z).setColor(1F, 1F, 0F, alpha);
    }

    private static void renderZaps(FensuBlockEntity fensu, PoseStack poses, MultiBufferSource buffers) {
        long time = fensu.getLevel() == null ? 0L : fensu.getLevel().getGameTime();
        Random random = new Random(time / 5L);
        random.nextBoolean();
        if (random.nextBoolean()) bolt(poses, buffers, random, 3.125D, 5.5D, 0D, 1.75D, 2.875D, 3.75D);
        if (random.nextBoolean()) bolt(poses, buffers, random, -3.125D, 5.5D, 0D, -1.75D, 2.875D, 3.75D);
        if (random.nextBoolean()) bolt(poses, buffers, random, 3.125D, 5.5D, 0D, 1.75D, 2.875D, -3.75D);
        if (random.nextBoolean()) bolt(poses, buffers, random, -3.125D, 5.5D, 0D, -1.75D, 2.875D, -3.75D);
    }

    private static void bolt(PoseStack poses, MultiBufferSource buffers, Random random,
                             double startX, double startY, double startZ,
                             double endX, double endY, double endZ) {
        int segments = 15;
        double[] x = new double[segments + 1];
        double[] y = new double[segments + 1];
        double[] z = new double[segments + 1];
        for (int index = 0; index <= segments; index++) {
            double progress = index / (double) segments;
            double envelope = Math.sin(Math.PI * progress) * 0.25D;
            x[index] = startX + (endX - startX) * progress + (random.nextDouble() - 0.5D) * envelope;
            y[index] = startY + (endY - startY) * progress + (random.nextDouble() - 0.5D) * envelope;
            z[index] = startZ + (endZ - startZ) * progress + (random.nextDouble() - 0.5D) * envelope;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = poses.last();
        for (int index = 0; index < segments; index++) {
            boltSegment(consumer, pose, x[index], y[index], z[index],
                    x[index + 1], y[index + 1], z[index + 1], 0.0625D, 0.25F, 0.25F, 0.25F);
            boltSegment(consumer, pose, x[index], y[index], z[index],
                    x[index + 1], y[index + 1], z[index + 1], 0.02D, 0F, 0.25F, 0.5F);
        }
    }

    private static void boltSegment(VertexConsumer consumer, PoseStack.Pose pose,
                                    double ax, double ay, double az, double bx, double by, double bz,
                                    double width, float red, float green, float blue) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double px = horizontal < 1.0E-6D ? width : -dz / horizontal * width;
        double pz = horizontal < 1.0E-6D ? 0D : dx / horizontal * width;
        quad(consumer, pose, ax + px, ay, az + pz, ax - px, ay, az - pz,
                bx - px, by, bz - pz, bx + px, by, bz + pz, red, green, blue);

        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double qx = length < 1.0E-6D ? 0D : -dx * dy / Math.max(horizontal * length, 1.0E-6D) * width;
        double qy = length < 1.0E-6D ? width : horizontal / length * width;
        double qz = length < 1.0E-6D ? 0D : -dz * dy / Math.max(horizontal * length, 1.0E-6D) * width;
        quad(consumer, pose, ax + qx, ay + qy, az + qz, ax - qx, ay - qy, az - qz,
                bx - qx, by - qy, bz - qz, bx + qx, by + qy, bz + qz, red, green, blue);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz,
                             float red, float green, float blue) {
        vertex(consumer, pose, ax, ay, az, red, green, blue);
        vertex(consumer, pose, bx, by, bz, red, green, blue);
        vertex(consumer, pose, cx, cy, cz, red, green, blue);
        vertex(consumer, pose, dx, dy, dz, red, green, blue);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               double x, double y, double z, float red, float green, float blue) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z).setColor(red, green, blue, 0.9F);
    }

    private static ModelResourceLocation model(String part) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "block/fensu_" + part));
    }

    private static float facingRotation(Direction facing) {
        return switch (facing) {
            case NORTH -> 270F;
            case SOUTH -> 90F;
            case WEST -> 0F;
            case EAST -> 180F;
            default -> 0F;
        };
    }

    @Override public AABB getRenderBoundingBox(FensuBlockEntity fensu) { return fensu.renderBounds(); }
    @Override public int getViewDistance() { return 256; }
}
