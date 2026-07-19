package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ChemicalPlantBlock;
import com.hbm.ntm.blockentity.ChemicalPlantBlockEntity;
import com.hbm.ntm.client.sound.ChemicalPlantSoundInstance;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChemicalPlantRenderer implements BlockEntityRenderer<ChemicalPlantBlockEntity> {
    private static final ResourceLocation FLUID_MODEL = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "models/block/chemical_plant_fluid.obj");
    private static final ResourceLocation FLUID_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/block/chemical_plant_fluid.png");
    private static final RenderType FLUID_TYPE = RenderType.create(
            "hbm_chemical_plant_fluid", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES, 4096, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(FLUID_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    public static final Map<String, ModelResourceLocation> MODELS = createModels();
    private final Map<ChemicalPlantBlockEntity, ChemicalPlantSoundInstance> sounds = new java.util.WeakHashMap<>();
    private final FluidMesh fluidMesh;
    public ChemicalPlantRenderer(BlockEntityRendererProvider.Context context) {
        fluidMesh = FluidMesh.load(Minecraft.getInstance().getResourceManager());
    }
    private static Map<String, ModelResourceLocation> createModels() {
        Map<String, ModelResourceLocation> models = new LinkedHashMap<>();
        for (String name : new String[]{"base", "frame", "spinner", "slider", "fluid"}) models.put(name,
                ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                        HbmNtm.MOD_ID, "block/chemical_plant_" + name)));
        return Map.copyOf(models);
    }
    @Override public void render(ChemicalPlantBlockEntity plant, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        if (plant.active()) {
            ChemicalPlantSoundInstance sound = sounds.get(plant);
            if (sound == null || sound.isStopped()) {
                sound = new ChemicalPlantSoundInstance(plant);
                sounds.put(plant, sound);
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        }
        float animation = plant.animation(partialTick);
        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(90F + facingRotation(
                plant.getBlockState().getValue(ChemicalPlantBlock.FACING))));
        renderModel("base", pose, buffers, light, overlay);
        if (plant.hasFrame()) renderModel("frame", pose, buffers, light, overlay);
        pose.pushPose();
        pose.translate(softPeak(animation * 0.125D) * 0.375D, 0D, 0D);
        renderModel("slider", pose, buffers, light, overlay);
        pose.popPose();
        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(animation * 15F % 360F));
        pose.translate(-0.5D, 0D, -0.5D);
        renderModel("spinner", pose, buffers, light, overlay);
        pose.popPose();
        if (plant.active() && plant.selectedRecipe() != null) {
            int color = plant.selectedRecipe().animationColor();
            renderFluid(pose, buffers, light, overlay, color, -animation / 100F,
                    (float) (softPeak(animation * 0.1D) * 0.1D - 0.25D));
        }
        pose.popPose();
    }
    private void renderModel(String name, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(MODELS.get(name));
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.solidBlockSheet());
        renderer.renderModel(pose.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                1F, 1F, 1F, light, overlay);
    }
    private void renderFluid(PoseStack pose, MultiBufferSource buffers, int light, int overlay, int color,
                             float uOffset, float vOffset) {
        VertexConsumer consumer = buffers.getBuffer(FLUID_TYPE);
        float red = (color >> 16 & 255) / 255F;
        float green = (color >> 8 & 255) / 255F;
        float blue = (color & 255) / 255F;
        for (FluidVertex vertex : fluidMesh.vertices) {
            consumer.addVertex(pose.last(), vertex.x, vertex.y, vertex.z)
                    .setColor(red, green, blue, 0.5F)
                    .setUv(vertex.u + uOffset, 1.0F - vertex.v + vOffset)
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose.last(), vertex.nx, vertex.ny, vertex.nz);
        }
    }

    private static double softPeak(double value) {
        return Math.sin(Math.PI * 0.5D * Math.cos(value));
    }

    private static float facingRotation(Direction direction) {
        return switch (direction) {
            case NORTH -> 0F; case EAST -> 90F; case SOUTH -> 180F; case WEST -> 270F; default -> 0F;
        };
    }
    @Override public AABB getRenderBoundingBox(ChemicalPlantBlockEntity plant) {
        BlockPos pos = plant.getBlockPos();
        return new AABB(pos.getX() - 1D, pos.getY(), pos.getZ() - 1D,
                pos.getX() + 2D, pos.getY() + 3D, pos.getZ() + 2D);
    }
    @Override public int getViewDistance() { return 256; }

    private record Point(float x, float y, float z) { }
    private record TexturePoint(float u, float v) { }
    private record FluidVertex(float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) { }

    private record FluidMesh(List<FluidVertex> vertices) {
        private static FluidMesh load(ResourceManager resources) {
            List<Point> positions = new ArrayList<>();
            List<TexturePoint> texturePoints = new ArrayList<>();
            List<Point> normals = new ArrayList<>();
            List<FluidVertex> vertices = new ArrayList<>();
            try (BufferedReader reader = resources.openAsReader(FLUID_MODEL)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] values = line.trim().split("\\s+");
                    if (values.length == 0) continue;
                    switch (values[0]) {
                        case "v" -> positions.add(new Point(Float.parseFloat(values[1]),
                                Float.parseFloat(values[2]), Float.parseFloat(values[3])));
                        case "vt" -> texturePoints.add(new TexturePoint(Float.parseFloat(values[1]),
                                Float.parseFloat(values[2])));
                        case "vn" -> normals.add(new Point(Float.parseFloat(values[1]),
                                Float.parseFloat(values[2]), Float.parseFloat(values[3])));
                        case "f" -> {
                            FluidVertex first = vertex(values[1], positions, texturePoints, normals);
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
                throw new IllegalStateException("Could not load chemical plant fluid mesh " + FLUID_MODEL,
                        exception);
            }
            if (vertices.isEmpty()) {
                throw new IllegalStateException("Chemical plant fluid mesh contains no faces: " + FLUID_MODEL);
            }
            return new FluidMesh(List.copyOf(vertices));
        }

        private static FluidVertex vertex(String value, List<Point> positions,
                                          List<TexturePoint> texturePoints, List<Point> normals) {
            String[] indices = value.split("/");
            Point position = positions.get(Integer.parseInt(indices[0]) - 1);
            TexturePoint texture = texturePoints.get(Integer.parseInt(indices[1]) - 1);
            Point normal = normals.get(Integer.parseInt(indices[2]) - 1);
            return new FluidVertex(position.x, position.y, position.z, texture.u, texture.v,
                    normal.x, normal.y, normal.z);
        }
    }
}
