package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.nuclear.FleijaRainbowCloudEntity;
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

/** Sphere shells: opaque catastrophe first, additive rainbow second. */
public final class FleijaRainbowCloudRenderer extends EntityRenderer<FleijaRainbowCloudEntity> {
    private static final ResourceLocation SPHERE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "models/sphere.obj");
    private static final RenderType OPAQUE = RenderType.create(
            "hbm_fleija_rainbow_core", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));
    private static final RenderType ADDITIVE = RenderType.create(
            "hbm_fleija_rainbow_shell", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 4096, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    // Translucent energy shells must not fill the shader pack's depth buffer.
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private final Mesh mesh;

    public FleijaRainbowCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
        mesh = Mesh.load(context.getResourceManager());
    }

    @Override
    public void render(FleijaRainbowCloudEntity cloud, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        float age = cloud.age();
        renderShell(cloud, poses.last(), buffers.getBuffer(OPAQUE), age * 0.5F);
        VertexConsumer additive = buffers.getBuffer(ADDITIVE);
        for (int i = 6; i <= 10; i++) {
            renderShell(cloud, poses.last(), additive, age * i / 10.0F);
        }
        super.render(cloud, yaw, partialTick, poses, buffers, packedLight);
    }

    private void renderShell(FleijaRainbowCloudEntity cloud, PoseStack.Pose pose,
                             VertexConsumer consumer, float radius) {
        float red = cloud.level().random.nextInt(256) / 255.0F;
        float green = cloud.level().random.nextInt(256) / 255.0F;
        float blue = cloud.level().random.nextInt(256) / 255.0F;
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
    public ResourceLocation getTextureLocation(FleijaRainbowCloudEntity entity) {
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
                throw new IllegalStateException("Could not load original FLEIJA sphere mesh " + SPHERE, exception);
            }
            if (vertices.size() != 42 || faces.size() != 80) {
                throw new IllegalStateException("Unexpected FLEIJA sphere mesh: "
                        + vertices.size() + " vertices, " + faces.size() + " faces");
            }
            return new Mesh(List.copyOf(vertices), List.copyOf(faces));
        }

        private static int index(String faceValue) {
            return Integer.parseInt(faceValue.substring(0, faceValue.indexOf('/'))) - 1;
        }
    }
}
