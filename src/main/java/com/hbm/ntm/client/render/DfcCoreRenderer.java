package com.hbm.ntm.client.render;

import com.hbm.ntm.blockentity.DfcCoreBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.Random;

public final class DfcCoreRenderer implements BlockEntityRenderer<DfcCoreBlockEntity> {
    private static final RenderType SPHERE_SOLID = RenderType.create(
            "hbm_dfc_core_solid", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4_096,
            false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));
    private static final RenderType SPHERE_GLOW = RenderType.create(
            "hbm_dfc_core_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65_536,
            false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.CULL)
                    // Transparent pulse shells must not populate the depth buffer or
                    // they hide component block-entity models rendered afterward.
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    private static final RenderType FLARE_GLOW = RenderType.create(
            "hbm_dfc_core_flare", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 16_384,
            false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    public DfcCoreRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(DfcCoreBlockEntity core, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        PoseStack.Pose pose = poses.last();
        if (!core.isReacting()) {
            sphere(buffers.getBuffer(SPHERE_SOLID), pose, 0.25F, 0.5F, 0.5F, 0.5F, 1.0F);
            sphere(buffers.getBuffer(SPHERE_GLOW), pose, 0.3125F, 0.1F, 0.1F, 0.1F, 0.7F);
        } else if (core.meltdownTick()) {
            flare(buffers.getBuffer(FLARE_GLOW), pose, core.color(), core.getLevel().getGameTime() + partialTick);
        } else {
            float fill = (core.tank(0).amount() + core.tank(1).amount()) / 256_000.0F;
            float radius = (0.5F + 4.5F * fill) * 0.125F;
            int color = core.color();
            float red = (color >> 16 & 255) / 255.0F * 0.4F;
            float green = (color >> 8 & 255) / 255.0F * 0.4F;
            float blue = (color & 255) / 255.0F * 0.4F;
            sphere(buffers.getBuffer(SPHERE_SOLID), pose, radius, red, green, blue, 1.0F);
            VertexConsumer glow = buffers.getBuffer(SPHERE_GLOW);
            double time = core.getLevel() == null ? 0.0D : core.getLevel().getGameTime() + partialTick;
            double phase = time * 0.1D % (Math.PI * 2.0D);
            double t = 0.8D;
            float pulse = (float) (((1.0D / t) * Math.atan((t * Math.sin(phase))
                    / (1.0D - t * Math.cos(phase))) + 1.0D) * 0.5D);
            for (int i = 0; i <= 16; i++) {
                float scale = 1.0F + 0.25F * i + pulse * (20 - i) * 0.125F;
                sphere(glow, pose, radius * scale, red, green, blue,
                        Math.max(0.045F, 0.16F - i * 0.006F));
            }
        }
        poses.popPose();
    }

    private static void sphere(VertexConsumer consumer, PoseStack.Pose pose, float radius,
                               float red, float green, float blue, float alpha) {
        int latitudeSteps = 10;
        int longitudeSteps = 20;
        for (int latitude = 0; latitude < latitudeSteps; latitude++) {
            double low = -Math.PI * 0.5D + Math.PI * latitude / latitudeSteps;
            double high = -Math.PI * 0.5D + Math.PI * (latitude + 1) / latitudeSteps;
            float lowY = (float) Math.sin(low) * radius;
            float highY = (float) Math.sin(high) * radius;
            float lowRing = (float) Math.cos(low) * radius;
            float highRing = (float) Math.cos(high) * radius;
            for (int longitude = 0; longitude < longitudeSteps; longitude++) {
                double left = Math.PI * 2.0D * longitude / longitudeSteps;
                double right = Math.PI * 2.0D * (longitude + 1) / longitudeSteps;
                // The OBJ has outward-facing winding and expects back-face culling.
                face(consumer, pose,
                        (float) Math.cos(left) * lowRing, lowY, (float) Math.sin(left) * lowRing,
                        (float) Math.cos(left) * highRing, highY, (float) Math.sin(left) * highRing,
                        (float) Math.cos(right) * highRing, highY, (float) Math.sin(right) * highRing,
                        (float) Math.cos(right) * lowRing, lowY, (float) Math.sin(right) * lowRing,
                        red, green, blue, alpha);
            }
        }
    }

    private static void flare(VertexConsumer consumer, PoseStack.Pose pose, int color, double time) {
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        double phase = time * 0.2D % (Math.PI * 2.0D);
        double t = 0.8D;
        float scale = 0.875F + 0.125F * (float) (((1.0D / t) * Math.atan((t * Math.sin(phase))
                / (1.0D - t * Math.cos(phase))) + 1.0D) * 0.5D);
        double rotation = time / 200.0D * Math.PI * 0.5D;
        double sinRotation = Math.sin(rotation);
        double cosRotation = Math.cos(rotation);
        Random random = new Random(432L);
        for (int i = 0; i < 150; i++) {
            double y = random.nextDouble() * 2.0D - 1.0D;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double ring = Math.sqrt(1.0D - y * y);
            double rawX = Math.cos(angle) * ring;
            double rawZ = Math.sin(angle) * ring;
            double x = rawX * cosRotation - rawZ * sinRotation;
            double z = rawX * sinRotation + rawZ * cosRotation;
            float length = (5.0F + random.nextFloat() * 2.0F) * scale;
            float width = (1.0F + random.nextFloat()) * scale;
            double tangentLength = Math.sqrt(x * x + z * z);
            double tx = tangentLength < 1.0E-5D ? 1.0D : -z / tangentLength;
            double tz = tangentLength < 1.0E-5D ? 0.0D : x / tangentLength;
            double bx = y * tz;
            double by = z * tx - x * tz;
            double bz = -y * tx;
            float cx = (float) x * length;
            float cy = (float) y * length;
            float cz = (float) z * length;
            float ax = cx + (float) tx * width;
            float ay = cy;
            float az = cz + (float) tz * width;
            float bx2 = cx + (float) (-tx * 0.5D + bx * 0.866D) * width;
            float by2 = cy + (float) (by * 0.866D) * width;
            float bz2 = cz + (float) (-tz * 0.5D + bz * 0.866D) * width;
            float cx2 = cx + (float) (-tx * 0.5D - bx * 0.866D) * width;
            float cy2 = cy - (float) (by * 0.866D) * width;
            float cz2 = cz + (float) (-tz * 0.5D - bz * 0.866D) * width;
            flareTriangle(consumer, pose, ax, ay, az, bx2, by2, bz2, red, green, blue);
            flareTriangle(consumer, pose, bx2, by2, bz2, cx2, cy2, cz2, red, green, blue);
            flareTriangle(consumer, pose, cx2, cy2, cz2, ax, ay, az, red, green, blue);
        }
    }

    private static void face(VertexConsumer consumer, PoseStack.Pose pose,
                             float ax,float ay,float az,float bx,float by,float bz,
                             float cx,float cy,float cz,float dx,float dy,float dz,
                             float red,float green,float blue,float alpha) {
        vertex(consumer,pose,ax,ay,az,red,green,blue,alpha); vertex(consumer,pose,bx,by,bz,red,green,blue,alpha);
        vertex(consumer,pose,cx,cy,cz,red,green,blue,alpha); vertex(consumer,pose,dx,dy,dz,red,green,blue,alpha);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x,float y,float z,
                               float red,float green,float blue,float alpha) {
        consumer.addVertex(pose,x,y,z).setColor(red,green,blue,alpha);
    }

    private static void flareVertex(VertexConsumer consumer, PoseStack.Pose pose, float x,float y,float z,
                                    float red,float green,float blue,float alpha) {
        consumer.addVertex(pose,x,y,z).setColor(red,green,blue,alpha);
    }

    private static void flareTriangle(VertexConsumer consumer, PoseStack.Pose pose,
                                      float ax, float ay, float az, float bx, float by, float bz,
                                      float red, float green, float blue) {
        flareVertex(consumer, pose, 0.0F, 0.0F, 0.0F, red, green, blue, 1.0F);
        flareVertex(consumer, pose, ax, ay, az, red, green, blue, 0.0F);
        flareVertex(consumer, pose, bx, by, bz, red, green, blue, 0.0F);
    }

    @Override public AABB getRenderBoundingBox(DfcCoreBlockEntity core) {
        BlockPos pos = core.getBlockPos();
        return new AABB(pos.getX() - 8.0D, pos.getY() - 8.0D, pos.getZ() - 8.0D,
                pos.getX() + 9.0D, pos.getY() + 9.0D, pos.getZ() + 9.0D);
    }
    @Override public int getViewDistance() { return 256; }
}
