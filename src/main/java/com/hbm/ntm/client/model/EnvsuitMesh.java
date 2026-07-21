package com.hbm.ntm.client.model;

import com.hbm.ntm.HbmNtm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** M1TTY environment suit OBJ, disassembled into wearable pieces. */
public final class EnvsuitMesh {
    private static final float LEGACY_TEXTURE_INSET = 0.0005F;
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "models/armor/envsuit.obj");
    private static final Set<String> REQUIRED_GROUPS = Set.of(
            "Helmet", "Lamps", "Chest", "LeftArm", "RightArm",
            "LeftLeg", "RightLeg", "LeftFoot", "RightFoot");

    private final Map<String, List<Vertex>> groups;

    private EnvsuitMesh(Map<String, List<Vertex>> groups) {
        this.groups = groups;
    }

    public static EnvsuitMesh load(ResourceManager resources) {
        return load(resources, MODEL, REQUIRED_GROUPS, "M1TTY Environment Suit");
    }

    public static EnvsuitMesh load(ResourceManager resources, ResourceLocation model,
                                    Set<String> requiredGroups, String description) {
        List<Point> positions = new ArrayList<>();
        List<TexturePoint> texturePoints = new ArrayList<>();
        List<Point> normals = new ArrayList<>();
        Map<String, List<Vertex>> groups = new LinkedHashMap<>();
        String currentGroup = null;

        try (BufferedReader reader = resources.openAsReader(model)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] values = trimmed.split("\\s+");
                switch (values[0]) {
                    case "v" -> positions.add(new Point(
                            Float.parseFloat(values[1]),
                            Float.parseFloat(values[2]),
                            Float.parseFloat(values[3])));
                    case "vt" -> texturePoints.add(new TexturePoint(
                            Float.parseFloat(values[1]),
                            Float.parseFloat(values[2])));
                    case "vn" -> normals.add(new Point(
                            Float.parseFloat(values[1]),
                            Float.parseFloat(values[2]),
                            Float.parseFloat(values[3])));
                    case "o", "g" -> {
                        currentGroup = values[1];
                        groups.computeIfAbsent(currentGroup, ignored -> new ArrayList<>());
                    }
                    case "f" -> {
                        if (currentGroup == null) {
                            throw new IllegalStateException("Face before first OBJ group");
                        }
                        List<Vertex> vertices = groups.get(currentGroup);
                        List<Vertex> face = new ArrayList<>(values.length - 1);
                        for (int index = 1; index < values.length; index++) {
                            face.add(vertex(values[index], positions, texturePoints, normals));
                        }
                        face = legacyFace(face);
                        Vertex first = face.getFirst();
                        for (int index = 1; index < face.size() - 1; index++) {
                            vertices.add(first);
                            vertices.add(face.get(index));
                            vertices.add(face.get(index + 1));
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load " + description + " mesh " + model, exception);
        }

        for (String group : requiredGroups) {
            List<Vertex> vertices = groups.get(group);
            if (vertices == null || vertices.isEmpty()) {
                throw new IllegalStateException(description + " mesh is missing group " + group);
            }
        }

        Map<String, List<Vertex>> immutable = new LinkedHashMap<>();
        groups.forEach((name, vertices) -> immutable.put(name, List.copyOf(vertices)));
        return new EnvsuitMesh(Map.copyOf(immutable));
    }

    public void render(String group, PoseStack.Pose pose, VertexConsumer consumer,
                       float scale, int packedLight, int packedOverlay, int color) {
        List<Vertex> vertices = group(group);
        for (int index = 0; index < vertices.size(); index += 3) {
            emit(vertices.get(index), pose, consumer, scale, packedLight, packedOverlay, color);
            emit(vertices.get(index + 1), pose, consumer, scale, packedLight, packedOverlay, color);
            emit(vertices.get(index + 2), pose, consumer, scale, packedLight, packedOverlay, color);
            // Armor wants quads. Give every triangle a harmless spare corner.
            emit(vertices.get(index + 2), pose, consumer, scale, packedLight, packedOverlay, color);
        }
    }

    public void renderWithUv(String group, PoseStack.Pose pose, VertexConsumer consumer,
                             float scale, int packedLight, int packedOverlay, int color,
                             float u, float v) {
        List<Vertex> vertices = group(group);
        for (int index = 0; index < vertices.size(); index += 3) {
            emitUnshaded(vertices.get(index), pose, consumer, scale, packedLight, packedOverlay, color, u, v);
            emitUnshaded(vertices.get(index + 1), pose, consumer, scale, packedLight, packedOverlay, color, u, v);
            emitUnshaded(vertices.get(index + 2), pose, consumer, scale, packedLight, packedOverlay, color, u, v);
            emitUnshaded(vertices.get(index + 2), pose, consumer, scale, packedLight, packedOverlay, color, u, v);
        }
    }

    public void renderSolid(String group, PoseStack.Pose pose, VertexConsumer consumer,
                            float scale, int color) {
        List<Vertex> vertices = group(group);
        for (int index = 0; index < vertices.size(); index += 3) {
            emitSolid(vertices.get(index), pose, consumer, scale, color);
            emitSolid(vertices.get(index + 1), pose, consumer, scale, color);
            emitSolid(vertices.get(index + 2), pose, consumer, scale, color);
            emitSolid(vertices.get(index + 2), pose, consumer, scale, color);
        }
    }

    private static void emitSolid(Vertex vertex, PoseStack.Pose pose, VertexConsumer consumer,
                                  float scale, int color) {
        consumer.addVertex(pose, vertex.x() * scale, vertex.y() * scale, vertex.z() * scale)
                .setColor(color);
    }

    private static void emit(Vertex vertex, PoseStack.Pose pose, VertexConsumer consumer,
                             float scale, int packedLight, int packedOverlay, int color) {
        emitWithUv(vertex, pose, consumer, scale, packedLight, packedOverlay, color,
                vertex.u(), 1.0F - vertex.v());
    }

    private static void emitWithUv(Vertex vertex, PoseStack.Pose pose, VertexConsumer consumer,
                                   float scale, int packedLight, int packedOverlay, int color,
                                   float u, float v) {
        consumer.addVertex(pose, vertex.x() * scale, vertex.y() * scale, vertex.z() * scale)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, vertex.nx(), vertex.ny(), vertex.nz());
    }

    private static void emitUnshaded(Vertex vertex, PoseStack.Pose pose, VertexConsumer consumer,
                                     float scale, int packedLight, int packedOverlay, int color,
                                     float u, float v) {
        consumer.addVertex(pose, vertex.x() * scale, vertex.y() * scale, vertex.z() * scale)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                // Lamps ignore the sun and bring their own fixed, fully lit normal.
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    public List<Vertex> group(String name) {
        List<Vertex> vertices = groups.get(name);
        if (vertices == null) throw new IllegalArgumentException("Unknown OBJ group: " + name);
        return vertices;
    }

    private static Vertex vertex(String value, List<Point> positions,
                                 List<TexturePoint> texturePoints, List<Point> normals) {
        String[] indices = value.split("/");
        Point position = positions.get(resolveIndex(indices[0], positions.size()));
        TexturePoint texture = texturePoints.get(resolveIndex(indices[1], texturePoints.size()));
        Point normal = normals.get(resolveIndex(indices[2], normals.size()));
        return new Vertex(position.x(), position.y(), position.z(), texture.u(), texture.v(),
                normal.x(), normal.y(), normal.z());
    }

    /** Flat face normal plus the tiny UV inset that keeps the atlas behaved. */
    private static List<Vertex> legacyFace(List<Vertex> face) {
        float averageU = 0.0F;
        float averageV = 0.0F;
        for (Vertex vertex : face) {
            averageU += vertex.u();
            averageV += vertex.v();
        }
        averageU /= face.size();
        averageV /= face.size();

        Vertex a = face.get(0);
        Vertex b = face.get(1);
        Vertex c = face.get(2);
        float abX = b.x() - a.x();
        float abY = b.y() - a.y();
        float abZ = b.z() - a.z();
        float acX = c.x() - a.x();
        float acY = c.y() - a.y();
        float acZ = c.z() - a.z();
        float normalX = abY * acZ - abZ * acY;
        float normalY = abZ * acX - abX * acZ;
        float normalZ = abX * acY - abY * acX;
        float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length != 0.0F) {
            normalX /= length;
            normalY /= length;
            normalZ /= length;
        }

        List<Vertex> adjusted = new ArrayList<>(face.size());
        for (Vertex vertex : face) {
            float u = vertex.u() + (vertex.u() > averageU
                    ? -LEGACY_TEXTURE_INSET : LEGACY_TEXTURE_INSET);
            // Raw OBJ V means the post-flip inset points the other way here.
            float v = vertex.v() + (vertex.v() < averageV
                    ? LEGACY_TEXTURE_INSET : -LEGACY_TEXTURE_INSET);
            adjusted.add(new Vertex(vertex.x(), vertex.y(), vertex.z(), u, v,
                    normalX, normalY, normalZ));
        }
        return adjusted;
    }

    private static int resolveIndex(String value, int size) {
        int index = Integer.parseInt(value);
        return index > 0 ? index - 1 : size + index;
    }

    private record Point(float x, float y, float z) {
    }

    private record TexturePoint(float u, float v) {
    }

    public record Vertex(float x, float y, float z, float u, float v,
                         float nx, float ny, float nz) {
    }
}
