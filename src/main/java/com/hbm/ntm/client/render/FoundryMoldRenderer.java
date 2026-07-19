package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.FoundryMoldBlockEntity;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;

public final class FoundryMoldRenderer implements BlockEntityRenderer<FoundryMoldBlockEntity> {
    private static final ResourceLocation LAVA = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/models/machines/lava_gray.png");
    private final ItemRenderer itemRenderer;

    public FoundryMoldRenderer(BlockEntityRendererProvider.Context context) { itemRenderer = context.getItemRenderer(); }

    @Override public void render(FoundryMoldBlockEntity mold, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        renderItem(mold.getItem(FoundryMoldBlockEntity.MOLD), mold.moldRenderHeight(), mold, pose, buffers, light, overlay);
        ItemStack output = mold.getItem(FoundryMoldBlockEntity.OUTPUT);
        if (output.getItem() instanceof BlockItem blockItem) {
            renderBlockOutput(blockItem, mold.outputRenderHeight(), pose, buffers, light);
        } else {
            renderItem(output, mold.outputRenderHeight(), mold, pose, buffers, light, overlay);
        }
        FoundryMaterial material = mold.material();
        if (material == null || mold.amount() <= 0 || mold.capacity() <= 0) return;
        float y = (float) mold.moltenSurfaceHeight();
        int color = material.moltenColor();
        float red = (color >> 16 & 255) / 255F;
        float green = (color >> 8 & 255) / 255F;
        float blue = (color & 255) / 255F;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(LAVA));
        PoseStack.Pose current = pose.last();
        vertex(consumer, current, .125F, y, .125F, 0, 0, red, green, blue);
        vertex(consumer, current, .125F, y, .875F, 0, 1, red, green, blue);
        vertex(consumer, current, .875F, y, .875F, 1, 1, red, green, blue);
        vertex(consumer, current, .875F, y, .125F, 1, 0, red, green, blue);
    }

    private void renderItem(ItemStack stack, double height, FoundryMoldBlockEntity mold, PoseStack pose,
                            MultiBufferSource buffers, int light, int overlay) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        pose.translate(.5D, height, .5D);
        pose.mulPose(Axis.XP.rotationDegrees(90F));
        pose.scale(.75F, .75F, .75F);
        itemRenderer.renderStatic(stack.copyWithCount(1), ItemDisplayContext.FIXED, light, overlay,
                pose, buffers, mold.getLevel(), 0);
        pose.popPose();
    }

    /** Block outputs appear as one translucent top face, not a sideways inventory cube. */
    private void renderBlockOutput(BlockItem item, double height, PoseStack pose,
                                   MultiBufferSource buffers, int light) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer()
                .getBlockModel(item.getBlock().defaultBlockState()).getParticleIcon();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose current = pose.last();
        blockVertex(consumer, current, .125F, (float) height, .125F, sprite.getU0(), sprite.getV1(), light);
        blockVertex(consumer, current, .125F, (float) height, .875F, sprite.getU1(), sprite.getV1(), light);
        blockVertex(consumer, current, .875F, (float) height, .875F, sprite.getU1(), sprite.getV0(), light);
        blockVertex(consumer, current, .875F, (float) height, .125F, sprite.getU0(), sprite.getV0(), light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, float red, float green, float blue) {
        consumer.addVertex(pose, x, y, z).setColor(red, green, blue, 1F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0F, 1F, 0F);
    }

    private static void blockVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                                    float u, float v, int light) {
        consumer.addVertex(pose, x, y, z).setColor(1F, 1F, 1F, .3F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0F, 1F, 0F);
    }
}
