package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.nuclear.SoliniumCloudEntity;
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

/** Teal core and three shells. Shockwave budget was spent on lightning. */
public final class SoliniumCloudRenderer extends EntityRenderer<SoliniumCloudEntity> {
    private static final ResourceLocation SPHERE_NEW = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "models/sphere_new.obj");
    // 0x27FFDA
    private static final float RED = 0x27 / 255.0F;
    private static final float GREEN = 0xFF / 255.0F;
    private static final float BLUE = 0xDA / 255.0F;
    private static final RenderType OPAQUE = RenderType.create(
            "hbm_solinium_cloud_core", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));
    private static final RenderType ADDITIVE = RenderType.create(
            "hbm_solinium_cloud_shell", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 4096, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    // Translucent energy shells must not fill the shader pack's depth buffer.
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private final Mesh mesh;

    public SoliniumCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
        mesh = Mesh.load(context.getResourceManager());
    }

    @Override
    public void render(SoliniumCloudEntity cloud, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        float radius = cloud.age() + partialTick;
        renderShell(poses.last(), buffers.getBuffer(OPAQUE), radius, 1.0F);
        VertexConsumer additive = buffers.getBuffer(ADDITIVE);
        for (int i = 0; i < 3; i++) {
            radius *= 1.025F;
            renderShell(poses.last(), additive, radius, 0.125F);
        }
        super.render(cloud, yaw, partialTick, poses, buffers, packedLight);
    }

    private void renderShell(PoseStack.Pose pose, VertexConsumer consumer, float radius, float alpha) {
        for (int[] face : mesh.faces) {
            vertex(consumer, pose, mesh.vertices.get(face[0]), radius, alpha);
            vertex(consumer, pose, mesh.vertices.get(face[1]), radius, alpha);
            vertex(consumer, pose, mesh.vertices.get(face[2]), radius, alpha);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Point point, float radius, float alpha) {
        consumer.addVertex(pose, point.x * radius, point.y * radius, point.z * radius)
                .setColor(RED, GREEN, BLUE, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(SoliniumCloudEntity entity) {
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
                throw new IllegalStateException("Could not load SOLINIUM cloud mesh " + SPHERE_NEW, exception);
            }
            if (vertices.size() != 162 || faces.size() != 320) {
                throw new IllegalStateException("Unexpected SOLINIUM cloud mesh: "
                        + vertices.size() + " vertices, " + faces.size() + " faces");
            }
            return new Mesh(List.copyOf(vertices), List.copyOf(faces));
        }

        private static int index(String faceValue) {
            return Integer.parseInt(faceValue.substring(0, faceValue.indexOf('/'))) - 1;
        }
    }
}
