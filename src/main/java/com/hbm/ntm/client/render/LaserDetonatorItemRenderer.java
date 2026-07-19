package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.IQuadTransformer;

import java.util.List;
import java.util.Random;

/** Laser detonator, now with blinking lights and a medically concerning waveform. */
public final class LaserDetonatorItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation MAIN = model("detonator_laser_main");
    public static final ModelResourceLocation LIGHTS = model("detonator_laser_lights");
    private static final RenderType FLAT_LIGHTS = RenderType.create(
            "hbm_laser_detonator_lights", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1_024, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    public LaserDetonatorItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        setupContext(context, poses);

        renderModel(MAIN, poses, buffers, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F);
        renderFlatLights(poses, buffers);
        renderWaveform(poses, buffers);
        renderDisplay(poses, buffers);
        poses.popPose();
    }

    private static void setupContext(ItemDisplayContext context, PoseStack poses) {
        // Undo slot centering; this OBJ still believes the origin is in 2014.
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                poses.scale(0.25F, 0.25F, 0.25F);
                poses.mulPose(Axis.YP.rotationDegrees(80.0F));
                poses.mulPose(Axis.XN.rotationDegrees(20.0F));
                poses.translate(1.0D, 0.5D, 3.0D);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> setupThirdPerson(context, poses);
            case GUI -> {
                // Bridge slot center to the old top-left inventory origin.
                poses.translate(-0.5D, 0.5D, 0.0D);
                poses.scale(1.0F, -1.0F, 1.0F);
                poses.scale(3.5F / 16.0F, 3.5F / 16.0F, -3.5F / 16.0F);
                poses.translate(1.5D, 2.75D, 0.0D);
                poses.mulPose(Axis.XP.rotationDegrees(180.0F));
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
                poses.mulPose(Axis.XN.rotationDegrees(45.0F));
            }
            case GROUND, FIXED -> {
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
                poses.scale(0.25F, 0.25F, 0.25F);
            }
            default -> {
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
                poses.scale(0.25F, 0.25F, 0.25F);
            }
        }
    }

    private static void setupThirdPerson(ItemDisplayContext context, PoseStack poses) {
        float side = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -1.0F : 1.0F;

        // Old full-3D hand bridge, then the detonator pose.
        poses.translate(-side / 16.0D, -0.125D, 0.625D);
        poses.mulPose(Axis.YN.rotationDegrees(180.0F));
        poses.mulPose(Axis.XP.rotationDegrees(90.0F));
        poses.translate(-side / 16.0D, 0.4375D, 0.0625D);
        poses.translate(side * 0.25D, 0.1875D, -0.1875D);
        poses.scale(0.375F, 0.375F, 0.375F);
        poses.mulPose(Axis.ZP.rotationDegrees(side * 60.0F));
        poses.mulPose(Axis.XN.rotationDegrees(90.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(side * 20.0F));
        poses.translate(0.0D, -0.3D, 0.0D);
        poses.scale(1.5F, 1.5F, 1.5F);
        poses.mulPose(Axis.YP.rotationDegrees(side * 50.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(side * 335.0F));
        poses.translate(-side * 0.9375D, -0.0625D, 0.0D);

        poses.scale(-0.125F, -0.125F, -0.125F);
        poses.mulPose(Axis.YP.rotationDegrees(85.0F));
        poses.mulPose(Axis.XP.rotationDegrees(145.0F));
        poses.translate(-0.5D, -1.0D, 6.5D);
    }

    private static void renderWaveform(PoseStack poses, MultiBufferSource buffers) {
        poses.pushPose();
        poses.translate(0.5626D, 1.125D, -0.875D);
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = poses.last();
        float pixel = 0.0625F;
        int segments = 32;
        float width = pixel * 8.0F;
        float segmentLength = width / segments;
        double time = System.currentTimeMillis() / -100.0D;
        float amplitude = 0.075F;
        for (int i = 0; i < segments; i++) {
            float h0 = (float) Math.sin(i * 0.5D + time) * amplitude;
            float h1 = (float) Math.sin((i + 1) * 0.5D + time) * amplitude;
            vertex(consumer, pose, 0.0F, -pixel * 0.25F + h1, segmentLength * (i + 1));
            vertex(consumer, pose, 0.0F, pixel * 0.25F + h1, segmentLength * (i + 1));
            vertex(consumer, pose, 0.0F, pixel * 0.25F + h0, segmentLength * i);
            vertex(consumer, pose, 0.0F, -pixel * 0.25F + h0, segmentLength * i);
        }
        poses.popPose();
    }

    private static void renderDisplay(PoseStack poses, MultiBufferSource buffers) {
        Font font = Minecraft.getInstance().font;
        Random random = new Random(System.currentTimeMillis() / 500L);
        poses.pushPose();
        poses.translate(0.5625D, 1.3125D, 0.875D);
        poses.scale(0.01F, -0.01F, 0.01F);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.translate(3.0D, -2.0D, 0.2D);
        for (int row = 0; row < 3; row++) {
            String value = Integer.toString(random.nextInt(900_000) + 100_000);
            font.drawInBatch(value, 0.0F, row * 12.5F, 0xFF0000, false,
                    poses.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET,
                    0, LightTexture.FULL_BRIGHT);
        }
        poses.popPose();
    }

    private static void renderModel(ModelResourceLocation location, PoseStack poses,
                                    MultiBufferSource buffers, int light, int overlay,
                                    float red, float green, float blue) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(location);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        renderer.renderModel(poses.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                Blocks.IRON_BLOCK.defaultBlockState(), model, red, green, blue, light, overlay);
    }

    private static void renderFlatLights(PoseStack poses, MultiBufferSource buffers) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(LIGHTS);
        VertexConsumer consumer = buffers.getBuffer(FLAT_LIGHTS);
        RandomSource random = RandomSource.create();

        // Lights are raw baked positions: no atlas, normals or lightmap allowed near the red.
        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            renderFlatQuads(model.getQuads(Blocks.IRON_BLOCK.defaultBlockState(), direction, random),
                    poses.last(), consumer);
        }
        random.setSeed(42L);
        renderFlatQuads(model.getQuads(Blocks.IRON_BLOCK.defaultBlockState(), null, random),
                poses.last(), consumer);
    }

    private static void renderFlatQuads(List<BakedQuad> quads, PoseStack.Pose pose,
                                        VertexConsumer consumer) {
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                float x = Float.intBitsToFloat(vertices[offset]);
                float y = Float.intBitsToFloat(vertices[offset + 1]);
                float z = Float.intBitsToFloat(vertices[offset + 2]);
                consumer.addVertex(pose, x, y, z).setColor(1.0F, 0.0F, 0.0F, 1.0F);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z) {
        consumer.addVertex(pose, x, y, z).setColor(1.0F, 1.0F, 0.0F, 1.0F);
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "item/" + name));
    }
}
