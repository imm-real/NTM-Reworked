package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.item.QuadRocketLauncherItem;
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

/** Source ItemRenderQuadro, including the separate tube rack and rotating sight label. */
public final class QuadRocketLauncherItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MODEL = id("models/weapons/quadro.obj");
    private static final ResourceLocation LAUNCHER_TEXTURE =
            id("textures/models/weapons/quadro.png");
    private static final ResourceLocation ROCKET_TEXTURE =
            id("textures/models/weapons/quadro_rocket.png");
    private static final Set<String> GROUPS = Set.of("Launcher", "Rockets");
    private static final String LABEL = ">> <<";

    private EnvsuitMesh mesh;

    public QuadRocketLauncherItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof QuadRocketLauncherItem)) return;
        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean thirdPerson = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        long elapsed = System.currentTimeMillis() - ClientWeaponEvents.lastShot(stack);
        boolean flash = (firstPerson || thirdPerson) && elapsed >= 0L && elapsed < 150L;

        poses.pushPose();
        setupContext(context, poses, partial);
        if (firstPerson) {
            renderFirstPerson(stack, poses, buffers, light, overlay, partial);
            if (flash) renderFirstFlash(poses, buffers, elapsed / 150.0F,
                    ClientWeaponEvents.shotRandom(stack));
        } else {
            renderLauncher(poses, buffers, light, overlay);
            if (thirdPerson && flash) renderThirdFlash(poses, buffers, elapsed / 150.0F,
                    ClientWeaponEvents.shotRandom(stack));
        }
        poses.popPose();
    }

    private void renderFirstPerson(ItemStack stack, PoseStack poses,
                                   MultiBufferSource buffers, int light, int overlay,
                                   float partial) {
        poses.scale(1.75F, 1.75F, 1.75F);
        pivotX(poses, 0.0D, -1.0D, -1.0D, equipAngle(stack));
        poses.translate(0.0D, 0.0D, recoilOffset(stack));
        pivotX(poses, 0.0D, -1.0D, -1.0D, reloadAngle(stack));
        renderLauncher(poses, buffers, light, overlay);

        Vec push = reloadPush(stack);
        poses.pushPose();
        poses.translate(0.0D, -1.0D, 0.0D);
        pivotX(poses, 0.0D, 3.0D, 0.0D, push.y * 30.0D);
        poses.translate(0.0D, 0.0D, push.x * 3.0D);
        mesh().render("Rockets", poses.last(),
                buffers.getBuffer(RenderType.entityCutout(ROCKET_TEXTURE)),
                1.0F, light, overlay, -1);
        poses.popPose();

        if (ClientWeaponEvents.aimingProgress(partial) >= 1.0F) {
            renderSightLabel(poses, buffers);
        }
    }

    private static void renderSightLabel(PoseStack poses, MultiBufferSource buffers) {
        Font font = Minecraft.getInstance().font;
        float scale = 0.04F;
        poses.pushPose();
        poses.translate(-0.375D, 2.25D, 0.875D);
        poses.mulPose(Axis.YN.rotationDegrees(
                (float) (180L + (System.currentTimeMillis() / 2L) % 360L)));
        poses.translate(-font.width(LABEL) * 0.5D * scale, 0.0D, 0.0D);
        poses.scale(scale, -scale, scale);
        font.drawInBatch(LABEL, 0.0F, 0.0F, 0xFF00FFFF, false,
                poses.last().pose(), buffers, Font.DisplayMode.NORMAL,
                0, LightTexture.FULL_BRIGHT);
        poses.popPose();
    }

    private void renderLauncher(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        mesh().render("Launcher", poses.last(),
                buffers.getBuffer(RenderType.entityCutout(LAUNCHER_TEXTURE)),
                1.0F, light, overlay, -1);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) {
            mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(),
                    MODEL, GROUPS, "Quad Rocket Launcher");
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
                poses.scale(4.75F / 16.0F, 4.75F / 16.0F, 4.75F / 16.0F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
                poses.translate(0.0D, -1.0D, 0.0D);
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
                poses.translate(lerp(side * 2.0D, side * 1.2D, aim),
                        lerp(-2.8D, -2.4D, aim), 2.0D);
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
        poses.scale(7.5F, 7.5F, 7.5F);
        poses.translate(0.0D, -0.5D, -0.25D);
    }

    private static double equipAngle(ItemStack stack) {
        if (QuadRocketLauncherItem.animation(stack) != QuadRocketLauncherItem.GunAnimation.EQUIP) {
            return 0.0D;
        }
        double progress = Mth.clamp(animationTime(stack) / 500.0D, 0.0D, 1.0D);
        return 60.0D * (1.0D - Math.sin(progress * Math.PI * 0.5D));
    }

    private static double recoilOffset(ItemStack stack) {
        if (QuadRocketLauncherItem.animation(stack) != QuadRocketLauncherItem.GunAnimation.CYCLE) {
            return 0.0D;
        }
        double time = animationTime(stack);
        if (time < 50.0D) return lerp(0.0D, -0.5D, time / 50.0D);
        if (time < 100.0D) return lerp(-0.5D, 0.0D, (time - 50.0D) / 50.0D);
        return 0.0D;
    }

    private static double reloadAngle(ItemStack stack) {
        QuadRocketLauncherItem.GunAnimation animation = QuadRocketLauncherItem.animation(stack);
        double time = animationTime(stack);
        if (animation == QuadRocketLauncherItem.GunAnimation.RELOAD) {
            if (time < 500.0D) return 60.0D * sinFull(time / 500.0D);
            if (time < 2000.0D) return 60.0D;
            if (time < 2750.0D) {
                return 60.0D * (1.0D - sinFull((time - 2000.0D) / 750.0D));
            }
            return 0.0D;
        }
        if (animation == QuadRocketLauncherItem.GunAnimation.JAMMED
                || animation == QuadRocketLauncherItem.GunAnimation.INSPECT) {
            if (time < 750.0D) return 60.0D * sinFull(time / 750.0D);
            if (time < 1250.0D) return 60.0D;
            if (time < 2000.0D) {
                return 60.0D * (1.0D - sinFull((time - 1250.0D) / 750.0D));
            }
        }
        return 0.0D;
    }

    private static Vec reloadPush(ItemStack stack) {
        if (QuadRocketLauncherItem.animation(stack) != QuadRocketLauncherItem.GunAnimation.RELOAD) {
            return Vec.ZERO;
        }
        double time = animationTime(stack);
        if (time < 500.0D) return new Vec(-1.0D, -1.0D);
        if (time < 850.0D) return new Vec(-1.0D, lerp(-1.0D, 0.0D, (time - 500.0D) / 350.0D));
        if (time < 1850.0D) return new Vec(lerp(-1.0D, 0.0D, (time - 850.0D) / 1000.0D), 0.0D);
        return Vec.ZERO;
    }

    private static void renderFirstFlash(PoseStack poses, MultiBufferSource buffers,
                                         float progress, float random) {
        poses.pushPose();
        poses.translate(-1.0D, 0.75D, 6.5D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(random * 90.0F));
        poses.scale(0.75F, 0.75F, 0.75F);
        SednaMuzzleFlash.render(poses, buffers, progress, 7.5F);
        poses.popPose();
    }

    private static void renderThirdFlash(PoseStack poses, MultiBufferSource buffers,
                                         float progress, float random) {
        poses.pushPose();
        poses.translate(0.0D, 0.75D, 2.0D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(random * 90.0F));
        poses.scale(0.75F, 0.75F, 0.75F);
        SednaMuzzleFlash.render(poses, buffers, progress, 7.5F);
        poses.popPose();
    }

    private static double animationTime(ItemStack stack) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        return (QuadRocketLauncherItem.animationTimer(stack) + partial) * 50.0D;
    }

    private static void pivotX(PoseStack poses, double x, double y, double z, double angle) {
        poses.translate(x, y, z);
        poses.mulPose(Axis.XP.rotationDegrees((float) angle));
        poses.translate(-x, -y, -z);
    }

    private static double sinFull(double progress) {
        return (-Math.cos(Mth.clamp(progress, 0.0D, 1.0D) * Math.PI) + 1.0D) * 0.5D;
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }

    private record Vec(double x, double y) {
        private static final Vec ZERO = new Vec(0.0D, 0.0D);
    }
}
