package com.hbm.ntm.client.render;

import com.hbm.ntm.entity.NI4NIBeamEntity;
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

public final class NI4NIBeamRenderer extends EntityRenderer<NI4NIBeamEntity> {
    private static final RenderType TYPE = RenderType.create(
            "hbm_ni4ni_beam", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096,
            false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    public NI4NIBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(NI4NIBeamEntity beam, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        float age = Math.max(0.0F, Math.min(1.0F,
                1.0F - (beam.tickCount - 2.0F + partialTick) / NI4NIBeamEntity.LIFETIME));
        if (age <= 0.0F || beam.beamLength() <= 0.0F) return;

        poses.pushPose();
        Vec3 direction = beam.beamDirection();
        poses.mulPose(new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F,
                (float) direction.x, (float) direction.y, (float) direction.z));
        poses.scale(age * 0.5F + 0.5F, age * 0.5F + 0.5F, 1.0F);
        jagged(buffers.getBuffer(TYPE), poses.last(), beam.beamLength(),
                (beam.tickCount + beam.getId()) / 2L, age);
        poses.popPose();
        super.render(beam, yaw, partialTick, poses, buffers, packedLight);
    }

    private static void jagged(VertexConsumer out, PoseStack.Pose pose, float length,
                               long seed, float age) {
        Random random = new Random(seed);
        int segments = Math.max(1, (int) (length / 2.0F) + 1);
        Vector3f previous = new Vector3f();
        for (int index = 1; index <= segments; index++) {
            float z = length * index / segments;
            float taper = index == segments ? 0.0F : 1.0F;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            Vector3f next = new Vector3f((float) Math.cos(angle) * 0.3F * taper,
                    (float) Math.sin(angle) * 0.3F * taper, z);
            prism(out, pose, previous, next, 0.075F,
                    0xAA / 255.0F * age, 0xD2 / 255.0F * age, 0xE5 / 255.0F * age);
            prism(out, pose, previous, next, 0.035F, age, age, age);
            previous = next;
        }
    }

    private static void prism(VertexConsumer out, PoseStack.Pose pose, Vector3f from, Vector3f to,
                              float radius, float red, float green, float blue) {
        Vector3f direction = new Vector3f(to).sub(from).normalize();
        Vector3f side = Math.abs(direction.y) < 0.9F
                ? new Vector3f(direction).cross(0.0F, 1.0F, 0.0F).normalize().mul(radius)
                : new Vector3f(direction).cross(1.0F, 0.0F, 0.0F).normalize().mul(radius);
        Vector3f up = new Vector3f(direction).cross(side).normalize().mul(radius);
        Vector3f[] a = {new Vector3f(from).add(side), new Vector3f(from).add(up),
                new Vector3f(from).sub(side), new Vector3f(from).sub(up)};
        Vector3f[] b = {new Vector3f(to).add(side), new Vector3f(to).add(up),
                new Vector3f(to).sub(side), new Vector3f(to).sub(up)};
        for (int index = 0; index < 4; index++) {
            int next = (index + 1) & 3;
            vertex(out, pose, a[index], red, green, blue);
            vertex(out, pose, a[next], red, green, blue);
            vertex(out, pose, b[next], red, green, blue);
            vertex(out, pose, b[index], red, green, blue);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vector3f point,
                               float red, float green, float blue) {
        out.addVertex(pose, point.x, point.y, point.z).setColor(red, green, blue, 1.0F);
    }

    @Override public ResourceLocation getTextureLocation(NI4NIBeamEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
