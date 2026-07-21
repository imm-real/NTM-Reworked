package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.item.BolterItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public final class BolterItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MODEL = id("models/weapons/bolter.obj");
    private static final ResourceLocation TEXTURE = id("textures/models/weapons/bolter.png");
    private static final Set<String> GROUPS = Set.of("Body", "Mag", "Bullet", "Casing");
    private EnvsuitMesh mesh;

    public BolterItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof BolterItem)) return;
        boolean first = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        poses.pushPose();
        setupContext(context, poses);
        if (first) renderFirstPerson(stack, poses, buffers, light, overlay);
        else renderStatic(poses, buffers, light, overlay);
        poses.popPose();
    }

    private void renderFirstPerson(ItemStack stack, PoseStack poses, MultiBufferSource buffers,
                                   int light, int overlay) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float time = (BolterItem.animationTimer(stack) + partial) * 50.0F;
        Animation animation = animation(BolterItem.animation(stack), time);

        poses.scale(0.5F, 0.5F, 0.5F);
        poses.mulPose(Axis.XP.rotationDegrees(animation.recoil * 5.0F));
        poses.translate(0.0D, 0.0D, animation.recoil);
        poses.translate(0.0D, animation.tilt, 3.0D);
        poses.mulPose(Axis.XP.rotationDegrees(animation.tilt * 35.0F));
        poses.translate(0.0D, 0.0D, -3.0D);
        render("Body", poses, buffers, light, overlay);

        poses.pushPose();
        poses.translate(0.0D, 0.0D, 5.0D);
        poses.mulPose(Axis.XN.rotationDegrees(animation.magAngle));
        poses.translate(0.0D, 0.0D, -5.0D);
        render("Mag", poses, buffers, light, overlay);
        if (animation.bullet) render("Bullet", poses, buffers, light, overlay);
        poses.popPose();

        renderCounter(stack, poses, buffers);
    }

    private void renderStatic(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        render("Body", poses, buffers, light, overlay);
        render("Mag", poses, buffers, light, overlay);
        render("Bullet", poses, buffers, light, overlay);
        render("Casing", poses, buffers, light, overlay);
    }

    private static void renderCounter(ItemStack stack, PoseStack poses, MultiBufferSource buffers) {
        Font font = Minecraft.getInstance().font;
        String rounds = Integer.toString(BolterItem.rounds(stack));
        poses.pushPose();
        poses.translate(0.025D - font.width(rounds) * 0.02D, 2.11D, 2.91D);
        poses.scale(0.04F, -0.04F, 0.04F);
        poses.mulPose(Axis.XP.rotationDegrees(45.0F));
        font.drawInBatch(rounds, 0.0F, 0.0F, 0xFFFF0000, false,
                poses.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET,
                0, LightTexture.FULL_BRIGHT);
        poses.popPose();
    }

    private void render(String group, PoseStack poses, MultiBufferSource buffers,
                        int light, int overlay) {
        mesh().render(group, poses.last(), buffers.getBuffer(RenderType.entityCutout(TEXTURE)),
                1.0F, light, overlay, -1);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) {
            mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(), MODEL, GROUPS,
                    "Bolter");
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
                poses.scale(2.75F / 16.0F, 2.75F / 16.0F, 2.75F / 16.0F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
                poses.translate(-0.25D, -0.5D, 0.0D);
            }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                float aim = ClientWeaponEvents.aimingProgress(partial);
                double side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 1.0D : -1.0D;
                poses.mulPose(Axis.YP.rotationDegrees(180.0F));
                poses.translate(0.0D, 0.0D, 0.875D);
                poses.translate(lerp(side * 1.2D, 0.0D, aim),
                        lerp(-1.6D, -1.3125D, aim), lerp(2.0D, 1.25D, aim));
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                setupThirdPerson(context, poses);
                poses.scale(2.5F, 2.5F, 2.5F);
                poses.translate(0.0D, -0.75D, 1.25D);
            }
            case GROUND, FIXED -> {
                poses.scale(0.075F, 0.075F, 0.075F);
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

    private static Animation animation(BolterItem.GunAnimation type, float time) {
        if (type == BolterItem.GunAnimation.CYCLE) {
            float recoil = time < 25.0F ? lerp(0.0F, 1.0F, time / 25.0F)
                    : time < 100.0F ? lerp(1.0F, 0.0F, (time - 25.0F) / 75.0F) : 0.0F;
            return new Animation(recoil, 0.0F, 0.0F, true);
        }
        if (type == BolterItem.GunAnimation.RELOAD) {
            float tilt = time < 250.0F ? lerp(0.0F, 1.0F, time / 250.0F)
                    : time < 1_750.0F ? 1.0F
                    : time < 2_000.0F ? lerp(1.0F, 0.0F, (time - 1_750.0F) / 250.0F) : 0.0F;
            float angle = time < 500.0F ? 0.0F
                    : time < 1_000.0F ? lerp(0.0F, 150.0F, (time - 500.0F) / 500.0F)
                    : time < 1_500.0F ? lerp(60.0F, 0.0F, (time - 1_000.0F) / 500.0F) : 0.0F;
            return new Animation(0.0F, tilt, angle, time <= 0.0F || time >= 1_500.0F);
        }
        if (type == BolterItem.GunAnimation.JAMMED) {
            float tilt = time < 500.0F ? 0.0F
                    : time < 750.0F ? lerp(0.0F, 1.0F, (time - 500.0F) / 250.0F)
                    : time < 1_450.0F ? 1.0F
                    : time < 1_700.0F ? lerp(1.0F, 0.0F, (time - 1_450.0F) / 250.0F) : 0.0F;
            float mag = time < 750.0F ? 0.0F
                    : time < 1_000.0F ? lerp(0.0F, 36.0F, (time - 750.0F) / 250.0F)
                    : time < 1_250.0F ? lerp(36.0F, 0.0F, (time - 1_000.0F) / 250.0F) : 0.0F;
            return new Animation(0.0F, tilt, mag, true);
        }
        return new Animation(0.0F, 0.0F, 0.0F, true);
    }

    private static float lerp(float from, float to, float amount) {
        amount = Math.max(0.0F, Math.min(1.0F, amount));
        return from + (to - from) * amount;
    }

    private static double lerp(double from, double to, float amount) {
        return from + (to - from) * amount;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }

    private record Animation(float recoil, float tilt, float magAngle, boolean bullet) { }
}
