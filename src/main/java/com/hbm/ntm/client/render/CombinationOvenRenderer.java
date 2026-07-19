package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.CombinationOvenBlockEntity;
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
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Combination Oven OBJ with an animated RBMK-fire billboard. */
public final class CombinationOvenRenderer implements BlockEntityRenderer<CombinationOvenBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/combination_oven_body"));
    private static final ResourceLocation FIRE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/particle/rbmk_fire.png");
    private static final RenderType FIRE_TYPE = RenderType.create(
            "hbm_combination_oven_fire", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(FIRE, false, false))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    public CombinationOvenRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(CombinationOvenBlockEntity oven, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, 0D, 0.5D);
        ThermalModelRenderer.render(MODEL, poses, buffers, packedLight, packedOverlay);
        if (oven.wasOn()) renderFire(oven, partialTick, poses, buffers);
        poses.popPose();
    }

    private static void renderFire(CombinationOvenBlockEntity oven, float partialTick,
                                   PoseStack poses, MultiBufferSource buffers) {
        long time = oven.getLevel() == null ? 0L : oven.getLevel().getGameTime();
        int frame = (int) (((time + partialTick) / 2D) % 14D);
        float frameWidth = 1F / 14F;
        float u0 = frame % 5 * frameWidth;
        float u1 = u0 + frameWidth;

        poses.pushPose();
        poses.translate(0D, 1.75D, 0D);
        poses.mulPose(Axis.YP.rotationDegrees(-Minecraft.getInstance().gameRenderer.getMainCamera().getYRot()));
        VertexConsumer consumer = buffers.getBuffer(FIRE_TYPE);
        PoseStack.Pose pose = poses.last();
        vertex(consumer, pose, -1F, 0F, u1, 1F);
        vertex(consumer, pose, -1F, 3F, u1, 0F);
        vertex(consumer, pose, 1F, 3F, u0, 0F);
        vertex(consumer, pose, 1F, 0F, u0, 1F);
        poses.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float u, float v) {
        consumer.addVertex(pose, x, y, 0F).setColor(1F, 1F, 1F, 1F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0F, 0F, 1F);
    }

    @Override public AABB getRenderBoundingBox(CombinationOvenBlockEntity oven) {
        BlockPos pos = oven.getBlockPos();
        return new AABB(pos.getX() - 1D, pos.getY(), pos.getZ() - 1D,
                pos.getX() + 2D, pos.getY() + 4.75D, pos.getZ() + 2D);
    }

    @Override public int getViewDistance() { return 256; }
}
