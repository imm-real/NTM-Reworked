package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.item.MissileLauncherItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Source ItemRenderMissileLauncher with its articulated breech and loaded missile. */
public final class MissileLauncherItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MODEL = id("models/weapons/missile_launcher.obj");
    private static final ResourceLocation TEXTURE =
            id("textures/models/weapons/missile_launcher.png");
    private static final Set<String> GROUPS = Set.of("Launcher", "Front", "Barrel", "Missile");
    private static final String LABEL = "AUTO";

    private EnvsuitMesh mesh;

    public MissileLauncherItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof MissileLauncherItem)) return;
        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean thirdPerson = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        long elapsed = System.currentTimeMillis() - ClientWeaponEvents.lastShot(stack);
        boolean flash = (firstPerson || thirdPerson) && elapsed >= 0L && elapsed < 75L;

        poses.pushPose();
        setupContext(context, poses, partial);
        if (firstPerson) {
            renderFirstPerson(stack, poses, buffers, light, overlay, partial);
        } else {
            renderStatic(stack, poses, buffers, light, overlay);
        }
        if (flash) renderFlash(poses, buffers, elapsed / 75.0F,
                ClientWeaponEvents.shotRandom(stack));
        poses.popPose();
    }

    private void renderFirstPerson(ItemStack stack, PoseStack poses,
                                   MultiBufferSource buffers, int light, int overlay,
                                   float partial) {
        poses.scale(0.5F, 0.5F, 0.5F);
        pivotX(poses, 0.0D, -2.0D, -2.0D, equipAngle(stack));
        render("Launcher", poses, buffers, light, overlay);

        poses.pushPose();
        pivotX(poses, 0.0D, 0.25D, 1.6875D, openAngle(stack));
        render("Front", poses, buffers, light, overlay);

        poses.pushPose();
        poses.translate(0.0D, 0.0D, barrelOffset(stack));
        render("Barrel", poses, buffers, light, overlay);
        poses.popPose();

        Vec missile = missilePosition(stack);
        poses.pushPose();
        poses.translate(missile.x, missile.y, missile.z);
        render("Missile", poses, buffers, light, overlay);
        poses.popPose();
        poses.popPose();

        if (ClientWeaponEvents.aimingProgress(partial) >= 1.0F) {
            renderSightLabel(poses, buffers);
        }
    }

    private void renderStatic(ItemStack stack, PoseStack poses,
                              MultiBufferSource buffers, int light, int overlay) {
        render("Launcher", poses, buffers, light, overlay);
        render("Barrel", poses, buffers, light, overlay);
        render("Front", poses, buffers, light, overlay);
        if (MissileLauncherItem.rounds(stack) > 0) {
            render("Missile", poses, buffers, light, overlay);
        }
    }

    private static void renderSightLabel(PoseStack poses, MultiBufferSource buffers) {
        Font font = Minecraft.getInstance().font;
        float scale = 0.04F;
        float variance = 0.7F + ((System.nanoTime() >>> 8) & 1023L) / 1023.0F * 0.3F;
        int color = 0xFF000000 | ((int) (variance * 255.0F) << 16);
        poses.pushPose();
        poses.translate(0.9375D, 2.25D,
                -0.5625D + font.width(LABEL) * 0.5D * scale);
        poses.scale(scale, -scale, scale);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        font.drawInBatch(LABEL, 0.0F, 0.0F, color, false,
                poses.last().pose(), buffers, Font.DisplayMode.NORMAL,
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
            mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(),
                    MODEL, GROUPS, "Missile Launcher");
        }
        return mesh;
    }

    private static void setupContext(ItemDisplayContext context, PoseStack poses, float partial) {
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case GUI -> {
                poses.scale(1.0F, -1.0F, 1.0F);
                poses.scale(1.0F, 1.0F, -1.0F);
                poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
                poses.scale(1.5F / 16.0F, 1.5F / 16.0F, 1.5F / 16.0F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
                poses.translate(0.0D, -0.5D, 0.0D);
            }
            case GROUND, FIXED -> {
                poses.scale(0.125F, 0.125F, 0.125F);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND ->
                    setupThirdPerson(context, poses);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                poses.mulPose(Axis.YP.rotationDegrees(180.0F));
                float aim = ClientWeaponEvents.aimingProgress(partial);
                double side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 1.0D : -1.0D;
                poses.translate(0.0D, 0.0D, 0.875D);
                poses.translate(lerp(side * 1.2D, side * 0.8D, aim),
                        -1.0D, lerp(0.4D, 0.0D, aim));
            }
            default -> {
                poses.scale(0.125F, 0.125F, 0.125F);
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
        poses.scale(2.5F, 2.5F, 2.5F);
        poses.translate(0.0D, -0.5D, -2.0D);
    }

    private static double equipAngle(ItemStack stack) {
        MissileLauncherItem.GunAnimation animation = MissileLauncherItem.animation(stack);
        double time = animationTime(stack);
        if (animation == MissileLauncherItem.GunAnimation.EQUIP) {
            return 60.0D * (1.0D - Math.sin(Mth.clamp(time / 1000.0D, 0.0D, 1.0D)
                    * Math.PI * 0.5D));
        }
        double hold = animation == MissileLauncherItem.GunAnimation.RELOAD ? 2250.0D : 1500.0D;
        if (animation == MissileLauncherItem.GunAnimation.RELOAD
                || animation == MissileLauncherItem.GunAnimation.JAMMED
                || animation == MissileLauncherItem.GunAnimation.INSPECT) {
            if (time < hold) return 0.0D;
            if (time < hold + 150.0D) {
                return -Math.sin((time - hold) / 150.0D * Math.PI * 0.5D);
            }
            if (time < hold + 300.0D) {
                return -1.0D + sinUp((time - hold - 150.0D) / 150.0D);
            }
        }
        return 0.0D;
    }

    private static double barrelOffset(ItemStack stack) {
        MissileLauncherItem.GunAnimation animation = MissileLauncherItem.animation(stack);
        if (animation != MissileLauncherItem.GunAnimation.RELOAD
                && animation != MissileLauncherItem.GunAnimation.JAMMED
                && animation != MissileLauncherItem.GunAnimation.INSPECT) return 0.0D;
        double time = animationTime(stack);
        double holdEnd = animation == MissileLauncherItem.GunAnimation.RELOAD ? 2250.0D : 1500.0D;
        if (time < 150.0D) return lerp(0.0D, 1.5D, time / 150.0D);
        if (time < holdEnd) return 1.5D;
        if (time < holdEnd + 150.0D) {
            return lerp(1.5D, 0.0D, (time - holdEnd) / 150.0D);
        }
        return 0.0D;
    }

    private static double openAngle(ItemStack stack) {
        MissileLauncherItem.GunAnimation animation = MissileLauncherItem.animation(stack);
        if (animation != MissileLauncherItem.GunAnimation.RELOAD
                && animation != MissileLauncherItem.GunAnimation.JAMMED
                && animation != MissileLauncherItem.GunAnimation.INSPECT) return 0.0D;
        double time = animationTime(stack);
        double holdEnd = animation == MissileLauncherItem.GunAnimation.RELOAD ? 1750.0D : 1000.0D;
        if (time < 250.0D) return 0.0D;
        if (time < 750.0D) return 90.0D * sinFull((time - 250.0D) / 500.0D);
        if (time < holdEnd) return 90.0D;
        if (time < holdEnd + 500.0D) {
            return 90.0D * (1.0D - sinFull((time - holdEnd) / 500.0D));
        }
        return 0.0D;
    }

    private static Vec missilePosition(ItemStack stack) {
        if (MissileLauncherItem.animation(stack) != MissileLauncherItem.GunAnimation.RELOAD) {
            return Vec.ZERO;
        }
        double time = animationTime(stack);
        if (time < 750.0D) return new Vec(-10.0D, 0.0D, 0.0D);
        if (time < 1100.0D) {
            double amount = sinFull((time - 750.0D) / 350.0D);
            return new Vec(lerp(3.0D, 0.0D, amount), 0.0D,
                    lerp(2.0D, -6.0D, amount));
        }
        if (time < 1450.0D) {
            return new Vec(0.0D, 0.0D,
                    lerp(-6.0D, 0.0D, sinUp((time - 1100.0D) / 350.0D)));
        }
        return Vec.ZERO;
    }

    private static void renderFlash(PoseStack poses, MultiBufferSource buffers,
                                    float progress, float random) {
        poses.pushPose();
        poses.translate(0.0D, 1.0D, 6.75D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(random * 90.0F));
        poses.scale(0.75F, 0.75F, 0.75F);
        SednaMuzzleFlash.render(poses, buffers, progress, 7.5F);
        poses.popPose();
    }

    private static double animationTime(ItemStack stack) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        return (MissileLauncherItem.animationTimer(stack) + partial) * 50.0D;
    }

    private static void pivotX(PoseStack poses, double x, double y, double z, double angle) {
        poses.translate(x, y, z);
        poses.mulPose(Axis.XP.rotationDegrees((float) angle));
        poses.translate(-x, -y, -z);
    }

    private static double sinFull(double progress) {
        return (-Math.cos(Mth.clamp(progress, 0.0D, 1.0D) * Math.PI) + 1.0D) * 0.5D;
    }

    private static double sinUp(double progress) {
        return 1.0D - Math.cos(Mth.clamp(progress, 0.0D, 1.0D) * Math.PI * 0.5D);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }

    private record Vec(double x, double y, double z) {
        private static final Vec ZERO = new Vec(0.0D, 0.0D, 0.0D);
    }
}
