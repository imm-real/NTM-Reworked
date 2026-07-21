package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.item.NI4NIItem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public final class NI4NIItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MODEL = id("models/weapons/n_i_4_n_i.obj");
    private static final ResourceLocation TEXTURE = id("textures/models/weapons/n_i_4_n_i.png");
    private static final ResourceLocation FLASH_TEXTURE = id("textures/models/weapons/laser_flash.png");
    private static final Set<String> GROUPS = Set.of("Barrel", "Coin4", "Coin3", "Coin2", "Coin1",
            "Grip", "FrameLight", "Cylinder", "CylinderHighlights", "FrameDark");
    private static final RenderType COLOR_TYPE = RenderType.create(
            "hbm_ni4ni_color", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256,
            false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    private static final RenderType FLASH_TYPE = RenderType.create(
            "hbm_ni4ni_flash", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
            false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(FLASH_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private EnvsuitMesh mesh;

    public NI4NIItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof NI4NIItem)) return;
        boolean first = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean held = first || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

        poses.pushPose();
        setupContext(context, poses);
        if (first) renderFirstPerson(stack, poses, buffers, light, overlay);
        else renderStatic(poses, buffers, light, overlay);
        long elapsed = System.currentTimeMillis() - ClientWeaponEvents.lastShot(stack);
        if (held && elapsed >= 0L && elapsed < 75L) renderFlash(stack, poses, buffers, elapsed / 75.0F);
        poses.popPose();
    }

    private void renderFirstPerson(ItemStack stack, PoseStack poses, MultiBufferSource buffers,
                                   int light, int overlay) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float time = (NI4NIItem.animationTimer(stack) + partial) * 50.0F;
        NI4NIItem.GunAnimation animation = NI4NIItem.animation(stack);

        poses.scale(0.3125F, 0.3125F, 0.3125F);
        pivotX(poses, 0.0D, 0.0D, -2.25D, equipAngle(animation, time));
        pivotX(poses, 0.0D, -1.0D, -6.0D,
                recoilAngle(animation, time, NI4NIItem.aiming(stack)));

        render("FrameDark", poses, buffers, light, overlay);
        render("Grip", poses, buffers, light, overlay);
        render("FrameLight", poses, buffers, light, overlay);
        poses.pushPose();
        poses.translate(0.0D, 1.1875D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees(drumAngle(animation, time)));
        poses.translate(0.0D, -1.1875D, 0.0D);
        render("Cylinder", poses, buffers, light, overlay);
        render("CylinderHighlights", poses, buffers, LightTexture.FULL_BRIGHT, overlay);
        poses.popPose();
        render("Barrel", poses, buffers, LightTexture.FULL_BRIGHT, overlay);

        int count = NI4NIItem.coinCount(stack);
        if (count > 3) renderCoin("Coin1", poses, buffers);
        if (count > 2) renderCoin("Coin2", poses, buffers);
        if (count > 1) renderCoin("Coin3", poses, buffers);
        if (count > 0) renderCoin("Coin4", poses, buffers);
    }

    private void renderStatic(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        render("FrameLight", poses, buffers, light, overlay);
        render("Cylinder", poses, buffers, light, overlay);
        render("Grip", poses, buffers, light, overlay);
        render("FrameDark", poses, buffers, light, overlay);
        render("CylinderHighlights", poses, buffers, LightTexture.FULL_BRIGHT, overlay);
        render("Barrel", poses, buffers, LightTexture.FULL_BRIGHT, overlay);
        renderCoin("Coin1", poses, buffers);
        renderCoin("Coin2", poses, buffers);
        renderCoin("Coin3", poses, buffers);
        renderCoin("Coin4", poses, buffers);
    }

    private void render(String group, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        mesh().render(group, poses.last(), buffers.getBuffer(RenderType.entityCutout(TEXTURE)),
                1.0F, light, overlay, -1);
    }

    private void renderCoin(String group, PoseStack poses, MultiBufferSource buffers) {
        mesh().renderSolid(group, poses.last(), buffers.getBuffer(COLOR_TYPE), 1.0F, 0xFF00FF00);
    }

    private static void renderFlash(ItemStack stack, PoseStack poses, MultiBufferSource buffers,
                                    float progress) {
        poses.pushPose();
        poses.translate(0.0D, 0.75D, 4.0D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(90.0F * ClientWeaponEvents.shotRandom(stack)));
        poses.scale(0.125F, 0.125F, 0.125F);
        float size = 7.5F * progress;
        VertexConsumer out = buffers.getBuffer(FLASH_TYPE);
        PoseStack.Pose pose = poses.last();
        flashVertex(out, pose, 0.0F, -size, -size, 1.0F, 1.0F);
        flashVertex(out, pose, 0.0F, size, -size, 0.0F, 1.0F);
        flashVertex(out, pose, 0.0F, size, size, 0.0F, 0.0F);
        flashVertex(out, pose, 0.0F, -size, size, 1.0F, 0.0F);
        poses.popPose();
    }

    private static void flashVertex(VertexConsumer out, PoseStack.Pose pose, float x, float y,
                                    float z, float u, float v) {
        out.addVertex(pose, x, y, z).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 1.0F, 0.0F, 0.0F);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) {
            mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(), MODEL, GROUPS,
                    "N I 4 N I");
        }
        return mesh;
    }

    private static void setupContext(ItemDisplayContext context, PoseStack poses) {
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case GUI -> {
                poses.scale(1.0F, -1.0F, -1.0F);
                poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
                poses.scale(0.3125F, 0.3125F, 0.3125F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                setupThirdPerson(context, poses);
                poses.translate(0.0D, 0.25D, 3.0D);
                poses.scale(1.5F, 1.5F, 1.5F);
            }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                float aim = ClientWeaponEvents.aimingProgress(partial);
                double side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 1.0D : -1.0D;
                poses.mulPose(Axis.YP.rotationDegrees(180.0F));
                poses.translate(0.0D, 0.0D, 1.875D);
                poses.translate(lerp(side * 0.8D, 0.0D, aim),
                        lerp(-0.8D, -0.625D, aim), lerp(0.8D, 0.125D, aim));
            }
            case GROUND -> {
                poses.scale(0.125F, 0.125F, 0.125F);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
            default -> {
                poses.scale(0.075F, 0.075F, 0.075F);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
        }
    }

    private static void setupThirdPerson(ItemDisplayContext context, PoseStack poses) {
        float side = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -1.0F : 1.0F;
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
        poses.scale(0.125F, 0.125F, 0.125F);
        poses.mulPose(Axis.ZP.rotationDegrees(side * 15.0F));
        poses.mulPose(Axis.YP.rotationDegrees(side * 12.5F));
        poses.mulPose(Axis.XP.rotationDegrees(15.0F));
        poses.translate(side * 3.5D, 0.0D, 0.0D);
    }

    private static float equipAngle(NI4NIItem.GunAnimation animation, float time) {
        if (animation == NI4NIItem.GunAnimation.EQUIP) {
            return lerp(0.0F, -720.0F, time / 500.0F);
        }
        if (animation == NI4NIItem.GunAnimation.INSPECT) {
            if (time < 750.0F) return lerp(0.0F, -1080.0F, time / 750.0F);
            if (time < 850.0F) return -1080.0F;
            if (time < 1600.0F) return lerp(-1080.0F, 0.0F, (time - 850.0F) / 750.0F);
        }
        return 0.0F;
    }

    private static float recoilAngle(NI4NIItem.GunAnimation animation, float time, boolean aimed) {
        if (animation != NI4NIItem.GunAnimation.CYCLE) return 0.0F;
        float recoil = aimed ? -5.0F : -30.0F;
        if (time < 100.0F) return lerp(0.0F, recoil, sineDown(time / 100.0F));
        if (time < 250.0F) return lerp(recoil, 0.0F, smooth((time - 100.0F) / 150.0F));
        return 0.0F;
    }

    private static float drumAngle(NI4NIItem.GunAnimation animation, float time) {
        if (animation != NI4NIItem.GunAnimation.CYCLE || time < 50.0F) return 0.0F;
        return lerp(0.0F, 120.0F, smooth((time - 50.0F) / 300.0F));
    }

    private static void pivotX(PoseStack poses, double x, double y, double z, float angle) {
        if (angle == 0.0F) return;
        poses.translate(x, y, z);
        poses.mulPose(Axis.XP.rotationDegrees(angle));
        poses.translate(-x, -y, -z);
    }

    private static float sineDown(float progress) {
        return (float) Math.sin(Math.PI * 0.5D * Mth.clamp(progress, 0.0F, 1.0F));
    }

    private static float smooth(float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        return (1.0F - (float) Math.cos(Math.PI * clamped)) * 0.5F;
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * Mth.clamp(progress, 0.0F, 1.0F);
    }

    private static double lerp(double from, double to, float progress) {
        return from + (to - from) * Mth.clamp(progress, 0.0F, 1.0F);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }
}
