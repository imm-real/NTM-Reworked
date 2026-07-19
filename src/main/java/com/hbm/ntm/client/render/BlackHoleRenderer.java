package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BlackHoleEntity;
import com.hbm.ntm.entity.RagingVortexEntity;
import com.hbm.ntm.entity.VortexEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Sphere.obj core, colored accretion swirl, and polar jets from RenderBlackHole. */
public final class BlackHoleRenderer<T extends BlackHoleEntity> extends EntityRenderer<T> {
    private static final ResourceLocation SPHERE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "models/sphere.obj");
    private static final RenderType CORE = RenderType.create(
            "hbm_black_hole_core", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES,
            4096, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));
    private static final RenderType GLOW = RenderType.create(
            "hbm_black_hole_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES,
            8192, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private final Mesh mesh;

    public BlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
        mesh = Mesh.load(context.getResourceManager());
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.scale(entity.size(), entity.size(), entity.size());
        renderCore(poses.last(), buffers.getBuffer(CORE));
        poses.mulPose(Axis.XP.rotationDegrees(entity.getId() % 90 - 45));
        poses.mulPose(Axis.YP.rotationDegrees(entity.getId() % 360));
        VertexConsumer glow = buffers.getBuffer(GLOW);
        if (entity instanceof VortexEntity) {
            poses.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * -5.0F));
            renderVortex(poses.last(), glow, 0x3898B3, 0.75F);
        } else if (entity instanceof RagingVortexEntity) {
            poses.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * -5.0F));
            renderVortex(poses.last(), glow, 0xE8390D, 0.25F);
            renderJets(poses.last(), glow);
        } else {
            renderDisc(entity, partialTick, poses, glow);
            renderJets(poses.last(), glow);
        }
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, packedLight);
    }

    private void renderCore(PoseStack.Pose pose, VertexConsumer consumer) {
        for (int[] face : mesh.faces) {
            vertex(consumer, pose, mesh.vertices.get(face[0]), 1.0F, 0, 0, 0, 1.0F);
            vertex(consumer, pose, mesh.vertices.get(face[1]), 1.0F, 0, 0, 0, 1.0F);
            vertex(consumer, pose, mesh.vertices.get(face[2]), 1.0F, 0, 0, 0, 1.0F);
        }
    }

    private static void renderDisc(BlackHoleEntity entity, float partialTick, PoseStack poses,
                                   VertexConsumer consumer) {
        for (int step = 0; step < 15; step++) {
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick)
                    * -(float) Math.pow(step + 1, 1.25D)));
            float inner = 3.0F - step * 0.175F;
            float[] color = discColor(step);
            annulus(consumer, poses.last(), inner, inner * 2.0F,
                    color[0], color[1], color[2], 0.75F);
            poses.popPose();
        }
    }

    private static void renderVortex(PoseStack.Pose pose, VertexConsumer consumer, int color, float alpha) {
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        annulus(consumer, pose, 0.9F, 3.0F, red, green, blue, alpha);
        annulus(consumer, pose, 3.0F, 6.0F, red, green, blue, alpha);
    }

    private static void annulus(VertexConsumer consumer, PoseStack.Pose pose,
                                float inner, float outer, float red, float green, float blue, float alpha) {
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            double a = MthAngle(i, segments);
            double b = MthAngle(i + 1, segments);
            float ix0 = (float) Math.cos(a) * inner, iz0 = (float) Math.sin(a) * inner;
            float ix1 = (float) Math.cos(b) * inner, iz1 = (float) Math.sin(b) * inner;
            float ox0 = (float) Math.cos(a) * outer, oz0 = (float) Math.sin(a) * outer;
            float ox1 = (float) Math.cos(b) * outer, oz1 = (float) Math.sin(b) * outer;
            colorVertex(consumer, pose, ix0, 0, iz0, red, green, blue, alpha);
            colorVertex(consumer, pose, ox0, 0, oz0, red, green, blue, 0);
            colorVertex(consumer, pose, ox1, 0, oz1, red, green, blue, 0);
            colorVertex(consumer, pose, ix0, 0, iz0, red, green, blue, alpha);
            colorVertex(consumer, pose, ox1, 0, oz1, red, green, blue, 0);
            colorVertex(consumer, pose, ix1, 0, iz1, red, green, blue, alpha);
        }
    }

    private static void renderJets(PoseStack.Pose pose, VertexConsumer consumer) {
        for (int direction : new int[]{-1, 1}) {
            for (int i = 0; i < 12; i++) {
                double a = MthAngle(i, 12);
                double b = MthAngle(i + 1, 12);
                colorVertex(consumer, pose, 0, 0, 0, 1, 1, 1, 0.35F);
                colorVertex(consumer, pose, (float) Math.cos(a) * 0.5F, direction * 10.0F,
                        (float) Math.sin(a) * 0.5F, 1, 1, 1, 0);
                colorVertex(consumer, pose, (float) Math.cos(b) * 0.5F, direction * 10.0F,
                        (float) Math.sin(b) * 0.5F, 1, 1, 1, 0);
            }
        }
    }

    private static double MthAngle(int step, int count) {
        return Math.PI * 2.0D * step / count;
    }

    private static float[] discColor(int step) {
        if (step < 5) return new float[]{1.0F, 0.125F + step * 0.1F, 0.0F};
        if (step == 5) return new float[]{1.0F, 1.0F, 0.0F};
        int i = step - 6;
        return new float[]{1.0F - i / 9.0F, 1.0F - i / 9.0F, i / 5.0F};
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Point point, float scale,
                               float red, float green, float blue, float alpha) {
        colorVertex(consumer, pose, point.x * scale, point.y * scale, point.z * scale,
                red, green, blue, alpha);
    }

    private static void colorVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                    float x, float y, float z,
                                    float red, float green, float blue, float alpha) {
        consumer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }

    private record Point(float x, float y, float z) { }
    private record Mesh(List<Point> vertices, List<int[]> faces) {
        private static Mesh load(ResourceManager resources) {
            List<Point> vertices = new ArrayList<>();
            List<int[]> faces = new ArrayList<>();
            try (BufferedReader reader = resources.openAsReader(SPHERE)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("v ")) {
                        String[] values = line.trim().split("\\s+");
                        vertices.add(new Point(Float.parseFloat(values[1]), Float.parseFloat(values[2]),
                                Float.parseFloat(values[3])));
                    } else if (line.startsWith("f ")) {
                        String[] values = line.trim().split("\\s+");
                        faces.add(new int[]{index(values[1]), index(values[2]), index(values[3])});
                    }
                }
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not load B93 singularity mesh " + SPHERE, exception);
            }
            if (vertices.size() != 42 || faces.size() != 80) {
                throw new IllegalStateException("Unexpected B93 singularity mesh: "
                        + vertices.size() + " vertices, " + faces.size() + " faces");
            }
            return new Mesh(List.copyOf(vertices), List.copyOf(faces));
        }

        private static int index(String faceValue) {
            return Integer.parseInt(faceValue.substring(0, faceValue.indexOf('/'))) - 1;
        }
    }
}
