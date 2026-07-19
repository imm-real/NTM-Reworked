package com.hbm.ntm.client.render;

import com.hbm.ntm.client.model.PowerFistRubbleModel;
import com.hbm.ntm.entity.PowerFistRubbleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

import java.util.List;

/** Ten degrees of rubble tumbling per tick. OSHA approved. */
public final class PowerFistRubbleRenderer extends EntityRenderer<PowerFistRubbleEntity> {
    private static final float INV_SQRT_THREE = (float) (1.0D / Math.sqrt(3.0D));
    private static final ResourceLocation STONE_TEXTURE = ResourceLocation.withDefaultNamespace(
            "textures/block/stone.png");
    private final PowerFistRubbleModel model;

    public PowerFistRubbleRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new PowerFistRubbleModel(context.bakeLayer(PowerFistRubbleModel.LAYER));
        shadowRadius = 0.0F;
    }

    @Override
    public void render(PowerFistRubbleEntity rubble, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        BlockState state = rubble.blockState();
        // AIR means spawn data is late. Drawing missingno would be rude.
        if (state.isAir()) {
            super.render(rubble, yaw, partialTick, poses, buffers, packedLight);
            return;
        }

        poses.pushPose();
        poses.mulPose(Axis.XP.rotationDegrees(180.0F));
        float spin = ((rubble.tickCount + partialTick) % 360.0F) * 10.0F;
        poses.mulPose(new Quaternionf().rotationAxis(
                spin * ((float) Math.PI / 180.0F), INV_SQRT_THREE, INV_SQRT_THREE, INV_SQRT_THREE));

        ResourceLocation texture = rawTexture(bottomSprite(state));
        // Direct PNG keeps the absurd repeating UVs away from atlas neighbors.
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(texture));
        model.render(poses, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poses.popPose();
        super.render(rubble, yaw, partialTick, poses, buffers, packedLight);
    }

    private static TextureAtlasSprite bottomSprite(BlockState state) {
        BakedModel baked = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        List<BakedQuad> bottom = baked.getQuads(state, Direction.DOWN, RandomSource.create(0L));
        return bottom.isEmpty() ? baked.getParticleIcon() : bottom.getFirst().getSprite();
    }

    private static ResourceLocation rawTexture(TextureAtlasSprite sprite) {
        ResourceLocation source = sprite.contents().name();
        if (source.equals(MissingTextureAtlasSprite.getLocation())) return STONE_TEXTURE;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                source.getNamespace(), "textures/" + source.getPath() + ".png");
        return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()
                ? texture
                : STONE_TEXTURE;
    }

    @Override
    public ResourceLocation getTextureLocation(PowerFistRubbleEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
