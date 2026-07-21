package com.hbm.ntm.client.render;

import com.hbm.ntm.entity.AberratorBeamEntity;
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

public final class AberratorBeamRenderer extends EntityRenderer<AberratorBeamEntity> {
    private static final RenderType TYPE = RenderType.create(
            "hbm_aberrator_beam", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 512, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    public AberratorBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(AberratorBeamEntity beam, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        float age = Math.max(0.0F, Math.min(1.0F,
                1.0F - (beam.tickCount - 2.0F + partialTick) / AberratorBeamEntity.LIFETIME));
        if (age <= 0.0F || beam.beamLength() <= 0.0F) return;
        poses.pushPose();
        Vec3 direction = beam.beamDirection();
        poses.mulPose(new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F,
                (float) direction.x, (float) direction.y, (float) direction.z));
        poses.scale(age * 5.0F, age * 5.0F, 1.0F);
        int near = beam.ammoType().blackLightning() ? 0x000000 : 0xFFFFFF;
        int far = beam.ammoType().blackLightning() ? 0x4C3093 : 0xE3D692;
        prism(buffers.getBuffer(TYPE), poses.last(), beam.beamLength(), near, far);
        poses.popPose();
        super.render(beam, yaw, partialTick, poses, buffers, packedLight);
    }

    private static void prism(VertexConsumer out, PoseStack.Pose pose, float length,
                              int nearColor, int farColor) {
        float front = 0.03125F;
        float back = front * 0.25F;
        quad(out, pose, back, -back, length, back, back, length,
                front, front, 0.0F, front, -front, 0.0F, farColor, nearColor);
        quad(out, pose, -back, back, length, -back, -back, length,
                -front, -front, 0.0F, -front, front, 0.0F, farColor, nearColor);
        quad(out, pose, -back, -back, length, back, -back, length,
                front, -front, 0.0F, -front, -front, 0.0F, farColor, nearColor);
        quad(out, pose, back, back, length, -back, back, length,
                -front, front, 0.0F, front, front, 0.0F, farColor, nearColor);
        face(out, pose, back, length, farColor);
        face(out, pose, front, 0.0F, nearColor);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             int farColor, int nearColor) {
        vertex(out, pose, ax, ay, az, farColor);
        vertex(out, pose, bx, by, bz, farColor);
        vertex(out, pose, cx, cy, cz, nearColor);
        vertex(out, pose, dx, dy, dz, nearColor);
    }

    private static void face(VertexConsumer out, PoseStack.Pose pose,
                             float radius, float z, int color) {
        vertex(out, pose, radius, radius, z, color);
        vertex(out, pose, radius, -radius, z, color);
        vertex(out, pose, -radius, -radius, z, color);
        vertex(out, pose, -radius, radius, z, color);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose,
                               float x, float y, float z, int color) {
        out.addVertex(pose, x, y, z).setColor(color | 0xFF000000);
    }

    @Override public ResourceLocation getTextureLocation(AberratorBeamEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
