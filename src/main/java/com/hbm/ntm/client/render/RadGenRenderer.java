package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.RadGenBlock;
import com.hbm.ntm.blockentity.RadGenBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Source Base/Rotor/Light/Glass passes, including the running-only wall-clock rotor. */
public final class RadGenRenderer implements BlockEntityRenderer<RadGenBlockEntity> {
    public static final ModelResourceLocation BASE = model("base");
    public static final ModelResourceLocation ROTOR = model("rotor");
    public static final ModelResourceLocation LIGHT = model("light");
    public static final ModelResourceLocation GLASS = model("glass");
    private static final RenderType MODEL_PASS = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
    private static final RenderType GLASS_PASS = RenderType.create(
            "hbm_radgen_glass", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 8_192, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    // Source pass disabled depth writes so internal geometry remained visible.
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    public RadGenRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(RadGenBlockEntity radGen, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(facingRotation(
                radGen.getBlockState().getValue(RadGenBlock.FACING))));

        renderModel(BASE, poses, buffers.getBuffer(MODEL_PASS),
                packedLight, packedOverlay, 1.0F, 1.0F, 1.0F);

        poses.pushPose();
        if (radGen.isOn()) {
            poses.translate(0.0D, 1.5D, 0.0D);
            poses.mulPose(Axis.XP.rotationDegrees((System.currentTimeMillis() % 3_600L) * -0.1F));
            poses.translate(0.0D, -1.5D, 0.0D);
        }
        renderModel(ROTOR, poses, buffers.getBuffer(MODEL_PASS),
                packedLight, packedOverlay, 1.0F, 1.0F, 1.0F);
        poses.popPose();

        float green = radGen.isOn() ? 1.0F : 0.1F;
        renderModel(LIGHT, poses, buffers.getBuffer(MODEL_PASS),
                LightTexture.FULL_BRIGHT, packedOverlay, 0.0F, green, 0.0F);

        VertexConsumer glass = new AlphaVertexConsumer(
                buffers.getBuffer(GLASS_PASS), 0.3F);
        renderModel(GLASS, poses, glass, packedLight, packedOverlay, 0.5F, 0.75F, 1.0F);
        // Draw textured glass again after translucency. It looks wrong when made sensible.
        renderModel(GLASS, poses, buffers.getBuffer(MODEL_PASS),
                packedLight, packedOverlay, 1.0F, 1.0F, 1.0F);
        poses.popPose();
    }

    public static void renderStatic(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        renderModel(BASE, poses, buffers.getBuffer(MODEL_PASS),
                light, overlay, 1.0F, 1.0F, 1.0F);
        renderModel(ROTOR, poses, buffers.getBuffer(MODEL_PASS),
                light, overlay, 1.0F, 1.0F, 1.0F);
        renderModel(LIGHT, poses, buffers.getBuffer(MODEL_PASS),
                LightTexture.FULL_BRIGHT, overlay, 0.0F, 1.0F, 0.0F);
    }

    private static void renderModel(ModelResourceLocation location, PoseStack poses,
                                    VertexConsumer consumer, int light, int overlay,
                                    float red, float green, float blue) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(location);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        renderer.renderModel(poses.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                red, green, blue, light, overlay);
    }

    private static ModelResourceLocation model(String part) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "block/radgen_" + part));
    }

    private static float facingRotation(Direction direction) {
        return switch (direction) {
            case EAST -> 0.0F;
            case NORTH -> 90.0F;
            case WEST -> 180.0F;
            case SOUTH -> 270.0F;
            default -> 0.0F;
        };
    }

    @Override public AABB getRenderBoundingBox(RadGenBlockEntity radGen) {
        BlockPos core = radGen.getBlockPos();
        Direction facing = radGen.getBlockState().getValue(RadGenBlock.FACING);
        AABB bounds = new AABB(core);
        for (BlockPos part : RadGenBlock.partPositions(core, facing)) bounds = bounds.minmax(new AABB(part));
        return bounds;
    }

    @Override public int getViewDistance() { return 256; }

    /** Overrides glass alpha without disturbing the baked OBJ data. */
    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override public VertexConsumer setColor(int red, int green, int blue, int sourceAlpha) {
            delegate.setColor(red, green, blue, Math.round(sourceAlpha * alpha));
            return this;
        }

        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
