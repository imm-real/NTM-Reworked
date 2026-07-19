package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.nuclear.FleijaCloudEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
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

/** Cyan core, three teal shells and one grey panic ring. */
public final class FleijaCloudRenderer extends EntityRenderer<FleijaCloudEntity> {
    private static final ResourceLocation SPHERE_NEW = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "models/sphere_new.obj");
    private static final RenderType OPAQUE = RenderType.create(
            "hbm_fleija_cloud_core", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));
    private static final RenderType ADDITIVE = RenderType.create(
            "hbm_fleija_cloud_shell", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 4096, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    // Translucent energy shells must not fill the shader pack's depth buffer.
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private final Mesh mesh;

    public FleijaCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
        mesh = Mesh.load(context.getResourceManager());
    }

    @Override
    public void render(FleijaCloudEntity cloud, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        int maxAge = cloud.maxAge();
        if (maxAge > 0) {
            float baseScale = (cloud.age() + partialTick) * 2.0F;
            float ageScale = baseScale / maxAge;

            float core = ageScale * 1.2F;
            if (core > 1.0F) core = Math.max(1.0F - (core - 1.0F) * 5.0F, 0.0F);
            core *= 2.0F * baseScale;

            renderShell(poses.last(), buffers.getBuffer(OPAQUE), core, 0.0F, 1.0F, 1.0F);

            VertexConsumer additive = buffers.getBuffer(ADDITIVE);
            float shell = core;
            for (int i = 0; i < 3; i++) {
                shell *= 1.05F;
                renderShell(poses.last(), additive, shell, 0.0F, 0.125F, 0.125F);
            }

            float shockwave = 5.0F * baseScale;
            float tint = (1.0F - ageScale) * 0.75F;
            renderShell(poses.last(), additive, shockwave, tint, tint, tint);
        }
        super.render(cloud, yaw, partialTick, poses, buffers, packedLight);
    }

    private void renderShell(PoseStack.Pose pose, VertexConsumer consumer, float radius,
                             float red, float green, float blue) {
        for (int[] face : mesh.faces) {
            vertex(consumer, pose, mesh.vertices.get(face[0]), radius, red, green, blue);
            vertex(consumer, pose, mesh.vertices.get(face[1]), radius, red, green, blue);
            vertex(consumer, pose, mesh.vertices.get(face[2]), radius, red, green, blue);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Point point, float radius,
                               float red, float green, float blue) {
        consumer.addVertex(pose, point.x * radius, point.y * radius, point.z * radius)
                .setColor(red, green, blue, 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(FleijaCloudEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }

    private record Point(float x, float y, float z) { }

    private record Mesh(List<Point> vertices, List<int[]> faces) {
        private static Mesh load(ResourceManager resources) {
            List<Point> vertices = new ArrayList<>();
            List<int[]> faces = new ArrayList<>();
            try (BufferedReader reader = resources.openAsReader(SPHERE_NEW)) {
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
                throw new IllegalStateException("Could not load FLEIJA cloud mesh " + SPHERE_NEW, exception);
            }
            if (vertices.size() != 162 || faces.size() != 320) {
                throw new IllegalStateException("Unexpected FLEIJA cloud mesh: "
                        + vertices.size() + " vertices, " + faces.size() + " faces");
            }
            return new Mesh(List.copyOf(vertices), List.copyOf(faces));
        }

        private static int index(String faceValue) {
            return Integer.parseInt(faceValue.substring(0, faceValue.indexOf('/'))) - 1;
        }
    }
}
