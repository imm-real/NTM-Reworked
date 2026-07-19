package com.hbm.ntm.client.model;

import com.hbm.ntm.HbmNtm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Ash Glasses OBJ, balanced delicately on the player's face. */
public final class AshGlassesModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "ash_glasses"), "main");
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "models/armor/goggles.obj");
    private static final float LEGACY_MODEL_SCALE = 1.0F / 16.0F;

    private final Mesh mesh;

    public AshGlassesModel(ModelPart root, ResourceManager resources) {
        super(root);
        mesh = Mesh.load(resources);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight,
                               int packedOverlay, int color) {
        if (!head.visible) return;

        poseStack.pushPose();
        head.translateAndRotate(poseStack);
        for (MeshVertex vertex : mesh.vertices()) {
            consumer.addVertex(poseStack.last(), vertex.x() * LEGACY_MODEL_SCALE,
                            vertex.y() * LEGACY_MODEL_SCALE, vertex.z() * LEGACY_MODEL_SCALE)
                    .setColor(color)
                    .setUv(vertex.u(), 1.0F - vertex.v())
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(poseStack.last(), vertex.nx(), vertex.ny(), vertex.nz());
        }
        poseStack.popPose();
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private record Point(float x, float y, float z) { }

    private record TexturePoint(float u, float v) { }

    private record MeshVertex(float x, float y, float z, float u, float v,
                              float nx, float ny, float nz) { }

    private record Mesh(List<MeshVertex> vertices) {
        private static Mesh load(ResourceManager resources) {
            List<Point> positions = new ArrayList<>();
            List<TexturePoint> texturePoints = new ArrayList<>();
            List<Point> normals = new ArrayList<>();
            List<MeshVertex> vertices = new ArrayList<>();

            try (BufferedReader reader = resources.openAsReader(MODEL)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    String[] values = trimmed.split("\\s+");
                    switch (values[0]) {
                        case "v" -> positions.add(new Point(Float.parseFloat(values[1]),
                                Float.parseFloat(values[2]), Float.parseFloat(values[3])));
                        case "vt" -> texturePoints.add(new TexturePoint(Float.parseFloat(values[1]),
                                Float.parseFloat(values[2])));
                        case "vn" -> normals.add(new Point(Float.parseFloat(values[1]),
                                Float.parseFloat(values[2]), Float.parseFloat(values[3])));
                        case "f" -> {
                            MeshVertex first = vertex(values[1], positions, texturePoints, normals);
                            for (int index = 2; index < values.length - 1; index++) {
                                vertices.add(first);
                                vertices.add(vertex(values[index], positions, texturePoints, normals));
                                vertices.add(vertex(values[index + 1], positions, texturePoints, normals));
                            }
                        }
                        default -> { }
                    }
                }
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not load Ash Glasses mesh " + MODEL, exception);
            }

            if (vertices.isEmpty()) {
                throw new IllegalStateException("Ash Glasses mesh contains no faces: " + MODEL);
            }
            return new Mesh(List.copyOf(vertices));
        }

        private static MeshVertex vertex(String value, List<Point> positions,
                                         List<TexturePoint> texturePoints, List<Point> normals) {
            String[] indices = value.split("/");
            Point position = positions.get(Integer.parseInt(indices[0]) - 1);
            TexturePoint texture = texturePoints.get(Integer.parseInt(indices[1]) - 1);
            Point normal = normals.get(Integer.parseInt(indices[2]) - 1);
            return new MeshVertex(position.x(), position.y(), position.z(), texture.u(), texture.v(),
                    normal.x(), normal.y(), normal.z());
        }
    }
}
