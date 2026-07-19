package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.ResearchReactorBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Source base/rod OBJs plus the submerged additive Cherenkov shell stack. */
public final class ResearchReactorRenderer implements BlockEntityRenderer<ResearchReactorBlockEntity> {
    public static final ModelResourceLocation BASE = model("research_reactor_base");
    public static final ModelResourceLocation RODS = model("research_reactor_rods");
    private static final RenderType GLOW = RenderType.create(
            "hbm_research_reactor_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4_096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    public ResearchReactorRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(ResearchReactorBlockEntity reactor, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(180.0F));
        ThermalModelRenderer.render(BASE, poses, buffers, light, overlay);

        double level = reactor.previousControlLevel()
                + (reactor.controlLevel() - reactor.previousControlLevel()) * partialTick;
        poses.pushPose();
        poses.translate(0.0D, level, 0.0D);
        ThermalModelRenderer.render(RODS, poses, buffers, light, overlay);
        poses.popPose();

        if (reactor.totalFlux() > 10 && reactor.isSubmerged()) {
            VertexConsumer glow = buffers.getBuffer(GLOW);
            for (double radius = 0.285D; radius < 0.7D; radius += 0.025D) {
                float alpha = 0.025F + (float) Math.random() * 0.015F
                        + 0.125F * reactor.totalFlux() / 1_000.0F;
                box(glow, poses.last(), (float) radius, alpha);
            }
        }
        poses.popPose();
    }

    public static void renderStatic(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        ThermalModelRenderer.render(BASE, poses, buffers, light, overlay);
        ThermalModelRenderer.render(RODS, poses, buffers, light, overlay);
    }

    private static void box(VertexConsumer consumer, PoseStack.Pose pose, float d, float alpha) {
        float low = 1.375F - d;
        float high = 1.375F + d;
        quad(consumer, pose, d, low, -d, d, high, -d, d, high, d, d, low, d, alpha);
        quad(consumer, pose, -d, low, -d, -d, high, -d, -d, high, d, -d, low, d, alpha);
        quad(consumer, pose, -d, low, d, -d, high, d, d, high, d, d, low, d, alpha);
        quad(consumer, pose, -d, low, -d, -d, high, -d, d, high, -d, d, low, -d, alpha);
        quad(consumer, pose, -d, high, -d, -d, high, d, d, high, d, d, high, -d, alpha);
        quad(consumer, pose, -d, low, -d, -d, low, d, d, low, d, d, low, -d, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float alpha) {
        vertex(consumer, pose, ax, ay, az, alpha);
        vertex(consumer, pose, bx, by, bz, alpha);
        vertex(consumer, pose, cx, cy, cz, alpha);
        vertex(consumer, pose, dx, dy, dz, alpha);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float alpha) {
        consumer.addVertex(pose, x, y, z).setColor(0.4F, 0.9F, 1.0F, alpha);
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "block/" + name));
    }

    @Override public AABB getRenderBoundingBox(ResearchReactorBlockEntity reactor) { return reactor.renderBounds(); }
    @Override public int getViewDistance() { return 256; }
}
