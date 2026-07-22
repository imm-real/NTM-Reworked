package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.item.PepperboxItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

public final class PepperboxItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation GRIP = model("pepperbox_grip");
    public static final ModelResourceLocation CYLINDER = model("pepperbox_cylinder");
    public static final ModelResourceLocation HAMMER = model("pepperbox_hammer");
    public static final ModelResourceLocation TRIGGER = model("pepperbox_trigger");
    public static final ModelResourceLocation SPEEDLOADER = model("pepperbox_speedloader");
    public static final ModelResourceLocation SHOT = model("pepperbox_shot");

    public PepperboxItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        setupContext(context, poses);

        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        AnimationState animation = firstPerson ? animation(stack) : AnimationState.NONE;
        poses.translate(animation.translateX, animation.translateY, animation.translateZ);
        poses.translate(0.0D, 0.0D, -5.0D);
        poses.mulPose(Axis.XN.rotationDegrees((float) animation.recoil));
        poses.translate(0.0D, 0.0D, 5.0D);

        if (firstPerson) {
            poses.pushPose();
            poses.translate(0.0D, 0.5D, 7.0D);
            poses.mulPose(Axis.YP.rotationDegrees(90.0F));
            WeaponSmokeRenderer.render(stack, 0, poses, buffers, 0.5D,
                    WeaponSmokeRenderer.STANDARD,
                    PepperboxItem.state(stack) == PepperboxItem.GunState.RELOADING);
            poses.popPose();
        }

        renderModel(GRIP, poses, buffers, packedLight, packedOverlay);

        poses.pushPose();
        poses.mulPose(Axis.ZP.rotationDegrees((float) animation.cylinder));
        renderModel(CYLINDER, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, 0.375D, -1.875D);
        poses.mulPose(Axis.XP.rotationDegrees((float) animation.hammer));
        poses.translate(0.0D, -0.375D, 1.875D);
        renderModel(HAMMER, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, 0.0D, -animation.trigger * 0.5D);
        renderModel(TRIGGER, poses, buffers, packedLight, packedOverlay);
        poses.popPose();

        if (firstPerson && animation.loaderVisible) {
            poses.pushPose();
            poses.translate(animation.loaderX, animation.loaderY, animation.loaderZ);
            renderModel(SPEEDLOADER, poses, buffers, packedLight, packedOverlay);
            if (animation.shotVisible) renderModel(SHOT, poses, buffers, packedLight, packedOverlay);
            poses.popPose();
        }

        boolean held = firstPerson || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        float flashProgress = -1.0F;
        long elapsed = System.currentTimeMillis() - ClientWeaponEvents.lastShot(stack);
        if (held && elapsed >= 0L && elapsed < 75L) {
            flashProgress = elapsed / 75.0F;
        } else if (held && PepperboxItem.animation(stack) == PepperboxItem.GunAnimation.CYCLE
                && PepperboxItem.animationTimer(stack) <= 2) {
            float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            flashProgress = Math.min((PepperboxItem.animationTimer(stack) + partial) * 50.0F / 75.0F, 1.0F);
        }
        if (flashProgress >= 0.0F) renderMuzzleFlash(poses, buffers, flashProgress);
        poses.popPose();
    }

    private static void setupContext(ItemDisplayContext context, PoseStack poses) {
        // ItemRenderer borrowed half a block on every axis. Take it back.
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case GUI -> {
                // Unflip the GUI or the gun balances on its grip like an idiot.
                poses.scale(1.0F, -1.0F, 1.0F);
                // Pixels became blocks. Divide by sixteen and pray.
                poses.scale(1.0F, 1.0F, -1.0F);
                poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
                poses.scale(1.5F / 16.0F, 1.5F / 16.0F, 1.5F / 16.0F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
                poses.translate(0.5D, 0.5D, 0.0D);
            }
            case GROUND -> {
                poses.scale(0.125F, 0.125F, 0.125F);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
            case FIXED -> {
                poses.scale(0.075F, 0.075F, 0.075F);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                boolean left = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                float side = left ? -1.0F : 1.0F;

                // Remove the new hand matrix; keep the actual arm transform.
                poses.translate(-side / 16.0D, -0.125D, 0.625D);
                poses.mulPose(Axis.YN.rotationDegrees(180.0F));
                poses.mulPose(Axis.XP.rotationDegrees(90.0F));

                // Old hand origin, mirrored because left hands were invented later.
                poses.translate(-side / 16.0D, 0.4375D, 0.0625D);

                // Generic hand matrix points the dangerous end away from the player.
                poses.translate(side * 0.25D, 0.1875D, -0.1875D);
                poses.scale(0.375F, 0.375F, 0.375F);
                poses.mulPose(Axis.ZP.rotationDegrees(side * 60.0F));
                poses.mulPose(Axis.XN.rotationDegrees(90.0F));
                poses.mulPose(Axis.ZP.rotationDegrees(side * 20.0F));

                // Ancient equipped-item seasoning.
                poses.translate(0.0D, -0.3D, 0.0D);
                poses.scale(1.5F, 1.5F, 1.5F);
                poses.mulPose(Axis.YP.rotationDegrees(side * 50.0F));
                poses.mulPose(Axis.ZP.rotationDegrees(side * 335.0F));
                poses.translate(-side * 0.9375D, -0.0625D, 0.0D);

                // Weapon base first, pepper second.
                poses.scale(0.125F, 0.125F, 0.125F);
                poses.mulPose(Axis.ZP.rotationDegrees(side * 15.0F));
                poses.mulPose(Axis.YP.rotationDegrees(side * 12.5F));
                poses.mulPose(Axis.XP.rotationDegrees(15.0F));
                poses.translate(side * 3.5D, 1.0D, 3.0D);
            }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                float aim = ClientWeaponEvents.aimingProgress(partial);
                double side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 1.0D : -1.0D;

                // Turn toward camera, then hip-to-sights.
                poses.mulPose(Axis.YP.rotationDegrees(180.0F));
                poses.translate(0.0D, 0.0D, 1.5D);
                poses.translate(lerp(side, 0.0D, aim), lerp(-0.6D, -0.3125D, aim),
                        lerp(0.8D, 0.5D, aim));
                poses.scale(0.25F, 0.25F, 0.25F);
            }
            default -> {
                poses.scale(0.075F, 0.075F, 0.075F);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
        }
    }

    private static double lerp(double start, double end, float progress) {
        return start + (end - start) * progress;
    }

    private static AnimationState animation(ItemStack stack) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        double millis = (PepperboxItem.animationTimer(stack) + partial) * 50.0D;
        PepperboxItem.GunAnimation type = PepperboxItem.animation(stack);
        return switch (type) {
            case CYCLE -> new AnimationState(
                    0, 0, 0,
                    sequence(millis, frame(0, 50), frame(45, 150, Curve.SIN_DOWN),
                            frame(45, 50), frame(0, 500, Curve.SIN_FULL)),
                    sequence(millis, frame(0, 1025), frame(60, 250)),
                    sequence(millis, frame(80, 25), frame(80, 1000), frame(0, 250)),
                    sequence(millis, frame(1, 25), frame(1, 250), frame(0, 100)),
                    0, 0, 0, false, false);
            case CYCLE_DRY -> new AnimationState(
                    0, 0, 0, 0,
                    sequence(millis, frame(0, 525), frame(60, 250)),
                    sequence(millis, frame(80, 25), frame(80, 500), frame(0, 250)),
                    sequence(millis, frame(1, 25), frame(1, 250), frame(0, 100)),
                    0, 0, 0, false, false);
            case EQUIP -> new AnimationState(0, 0, 0,
                    sequence(millis, frame(-45, 0), frame(0, 200, Curve.SIN_DOWN)), 0, 0, 0,
                    0, 0, 0, false, false);
            case RELOAD -> reloadAnimation(millis);
            case INSPECT -> new AnimationState(0, 0, 0,
                    sequence(millis, frame(-5, 200, Curve.SIN_UP), frame(0, 200, Curve.SIN_DOWN)),
                    sequence(millis, frame(-360, 750, Curve.SIN_FULL)), 0, 0,
                    0, 0, 0, false, false);
            case JAMMED -> new AnimationState(
                    0,
                    sequence(millis, frame(0, 500), frame(-6, 400, Curve.SIN_FULL),
                            frame(-6, 2000), frame(0, 400, Curve.SIN_FULL)),
                    0,
                    sequence(millis, frame(0, 500), frame(45, 400, Curve.SIN_FULL),
                            frame(45, 2000), frame(0, 400, Curve.SIN_FULL)),
                    sequence(millis, frame(0, 1300), frame(60, 500, Curve.SIN_FULL),
                            frame(60, 400), frame(0, 500, Curve.SIN_FULL)),
                    0, 0, 0, 0, 0, false, false);
            default -> AnimationState.NONE;
        };
    }

    private static AnimationState reloadAnimation(double millis) {
        double loaderY = sequence(millis, frame(0, 500), frame(5, 0), frame(0, 500, Curve.SIN_FULL),
                frame(0, 200), frame(0, 200), frame(0, 200), frame(5, 500, Curve.SIN_FULL), frame(0, 0));
        double loaderZ = sequence(millis, frame(0, 500), frame(-5, 0), frame(-0.1, 500, Curve.SIN_FULL),
                frame(-1, 200), frame(-1, 200), frame(-0.1, 200),
                frame(-5, 500, Curve.SIN_FULL), frame(0, 0));
        return new AnimationState(
                0,
                sequence(millis, frame(-12, 500, Curve.SIN_FULL), frame(-12, 700), frame(-13, 200),
                        frame(-12, 200), frame(-12, 500), frame(0, 500, Curve.SIN_FULL)),
                sequence(millis, frame(5, 500, Curve.SIN_FULL), frame(5, 700), frame(5, 200),
                        frame(5, 200), frame(5, 500), frame(0, 500, Curve.SIN_FULL)),
                sequence(millis, frame(90, 500, Curve.SIN_FULL), frame(90, 1600),
                        frame(0, 500, Curve.SIN_FULL), frame(-5, 200, Curve.SIN_UP),
                        frame(0, 200, Curve.SIN_DOWN)),
                sequence(millis, frame(0, 2600), frame(-360, 750, Curve.SIN_FULL)),
                0, 0,
                0, loaderY, loaderZ,
                Math.abs(loaderY) > 0.0001D || Math.abs(loaderZ) > 0.0001D,
                sequence(millis, frame(1, 1400), frame(0, 0)) != 0.0D);
    }

    private static Frame frame(double value, double duration) {
        return frame(value, duration, Curve.LINEAR);
    }

    private static Frame frame(double value, double duration, Curve curve) {
        return new Frame(value, duration, curve);
    }

    private static double sequence(double time, Frame... frames) {
        double elapsed = 0.0D;
        double previous = 0.0D;
        for (Frame frame : frames) {
            if (frame.duration <= 0.0D) {
                previous = frame.value;
                continue;
            }
            if (time < elapsed + frame.duration) {
                double progress = (time - elapsed) / frame.duration;
                progress = Math.max(0.0D, Math.min(progress, 1.0D));
                return previous + (frame.value - previous) * frame.curve.apply(progress);
            }
            elapsed += frame.duration;
            previous = frame.value;
        }
        return previous;
    }

    private static void renderModel(ModelResourceLocation location, PoseStack poses, MultiBufferSource buffers,
                                    int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(location);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        renderer.renderModel(poses.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
    }

    private static void renderMuzzleFlash(PoseStack poses, MultiBufferSource buffers, float progress) {
        poses.pushPose();
        poses.translate(0.0D, 0.5D, 7.0D);
        poses.scale(0.5F, 0.5F, 0.5F);
        SednaMuzzleFlash.render(poses, buffers, progress, 15.0F);
        poses.popPose();
    }

    private static void flashVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                    float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "item/" + path));
    }

    private record Frame(double value, double duration, Curve curve) { }

    private enum Curve {
        LINEAR { double apply(double x) { return x; } },
        SIN_UP { double apply(double x) { return -Math.sin((x * Math.PI + Math.PI) * 0.5D) + 1.0D; } },
        SIN_DOWN { double apply(double x) { return Math.sin(x * Math.PI * 0.5D); } },
        SIN_FULL { double apply(double x) { return (-Math.cos(x * Math.PI) + 1.0D) * 0.5D; } };
        abstract double apply(double x);
    }

    private record AnimationState(double translateX, double translateY, double translateZ,
                                  double recoil, double cylinder, double hammer, double trigger,
                                  double loaderX, double loaderY, double loaderZ,
                                  boolean loaderVisible, boolean shotVisible) {
        private static final AnimationState NONE = new AnimationState(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false);
    }
}
