package com.hbm.ntm.client.render;

import com.hbm.ntm.entity.FollyBeamEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

public final class FollyBeamRenderer extends EntityRenderer<FollyBeamEntity> {
    private static final RenderType TYPE = RenderType.create(
            "hbm_folly_beam", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192,
            false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    public FollyBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(FollyBeamEntity beam, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        float age = Math.max(0.0F, Math.min(1.0F,
                1.0F - (beam.tickCount - 2.0F + partialTick) / FollyBeamEntity.LIFETIME));
        if (age <= 0.0F) return;
        poses.pushPose();
        Vec3 direction = beam.beamDirection();
        poses.mulPose(new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F,
                (float) direction.x, (float) direction.y, (float) direction.z));
        VertexConsumer out = buffers.getBuffer(TYPE);
        float width = (1.0F - age) * 25.0F + 2.5F;
        float gray = 0x20 / 255.0F * age;
        poses.pushPose();
        poses.scale(width, width, 1.0F);
        jagged(out, poses.last(), beam.beamLength(), beam.tickCount / 3L,
                0.0F, 0.0625F, gray, gray, gray, age);
        poses.popPose();

        float flare = (1.0F - age) * 7.5F + 1.5F;
        orb(out, poses.last(), 0.0F, flare * 0.25F, 1.0F, 1.0F, 1.0F, 0.75F * age);
        if (beam.tickCount < 50) {
            float distance = (beam.tickCount + partialTick) * 10.0F;
            float scale = 2.0F + (beam.tickCount + partialTick) / 25.0F * 3.0F;
            orb(out, poses.last(), distance, scale * 0.25F,
                    0.75F, 0.75F, 0.75F, 0.75F);
        }
        poses.popPose();
        super.render(beam, yaw, partialTick, poses, buffers, packedLight);
    }

    private static void jagged(VertexConsumer out, PoseStack.Pose pose, float length, long seed,
                               float wander, float radius, float red, float green, float blue, float alpha) {
        Random random = new Random(seed);
        int segments = Math.max(1, (int) (length / 2.0F) + 1);
        Vector3f previous = new Vector3f();
        for (int i = 1; i <= segments; i++) {
            float z = length * i / segments;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            Vector3f next = new Vector3f((float) Math.cos(angle) * wander,
                    (float) Math.sin(angle) * wander, z);
            prism(out, pose, previous, next, radius, red, green, blue, alpha);
            previous = next;
        }
    }

    private static void prism(VertexConsumer out, PoseStack.Pose pose, Vector3f from, Vector3f to,
                              float radius, float red, float green, float blue, float alpha) {
        Vector3f direction = new Vector3f(to).sub(from).normalize();
        Vector3f side = Math.abs(direction.y) < 0.9F
                ? new Vector3f(direction).cross(0.0F, 1.0F, 0.0F).normalize().mul(radius)
                : new Vector3f(direction).cross(1.0F, 0.0F, 0.0F).normalize().mul(radius);
        Vector3f up = new Vector3f(direction).cross(side).normalize().mul(radius);
        Vector3f[] a = {new Vector3f(from).add(side), new Vector3f(from).add(up),
                new Vector3f(from).sub(side), new Vector3f(from).sub(up)};
        Vector3f[] b = {new Vector3f(to).add(side), new Vector3f(to).add(up),
                new Vector3f(to).sub(side), new Vector3f(to).sub(up)};
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            vertex(out, pose, a[i], red, green, blue, alpha);
            vertex(out, pose, a[j], red, green, blue, alpha);
            vertex(out, pose, b[j], red, green, blue, alpha);
            vertex(out, pose, b[i], red, green, blue, alpha);
        }
    }

    private static void orb(VertexConsumer out, PoseStack.Pose pose, float z, float radius,
                            float red, float green, float blue, float alpha) {
        quad(out, pose, -radius, 0, z, 0, -radius, z, radius, 0, z, 0, radius, z,
                red, green, blue, alpha);
        quad(out, pose, -radius, 0, z, 0, 0, z - radius, radius, 0, z, 0, 0, z + radius,
                red, green, blue, alpha);
        quad(out, pose, 0, -radius, z, 0, 0, z - radius, 0, radius, z, 0, 0, z + radius,
                red, green, blue, alpha);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float red, float green, float blue, float alpha) {
        vertex(out, pose, new Vector3f(ax, ay, az), red, green, blue, alpha);
        vertex(out, pose, new Vector3f(bx, by, bz), red, green, blue, alpha);
        vertex(out, pose, new Vector3f(cx, cy, cz), red, green, blue, alpha);
        vertex(out, pose, new Vector3f(dx, dy, dz), red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vector3f point,
                               float red, float green, float blue, float alpha) {
        out.addVertex(pose, point.x, point.y, point.z).setColor(red, green, blue, alpha);
    }

    @Override public ResourceLocation getTextureLocation(FollyBeamEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
