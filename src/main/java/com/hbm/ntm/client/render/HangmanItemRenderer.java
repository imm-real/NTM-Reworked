package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.item.HangmanItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Source ItemRenderHangman groups, transforms, animations, and muzzle plume. */
public final class HangmanItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "models/weapons/hangman.obj");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/models/weapons/hangman.png");
    private static final Set<String> GROUPS = Set.of("Rifle", "Internals", "Lid", "Magazine", "Bullets");

    private EnvsuitMesh mesh;

    public HangmanItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof HangmanItem)) return;
        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        poses.pushPose();
        setupContext(context, poses);
        if (firstPerson) renderFirstPerson(stack, poses, buffers, packedLight, packedOverlay);
        else renderStatic(poses, buffers, packedLight, packedOverlay);

        boolean held = firstPerson || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        long elapsed = System.currentTimeMillis() - ClientWeaponEvents.lastShot(stack);
        if (held && elapsed >= 0L && elapsed < 75L) {
            renderMuzzleFlash(poses, buffers, elapsed / 75.0F, ClientWeaponEvents.shotRandom(stack));
        }
        poses.popPose();
    }

    private void renderFirstPerson(ItemStack stack, PoseStack poses, MultiBufferSource buffers,
                                   int light, int overlay) {
        Animation animation = animation(stack);

        poses.translate(1.2D, 0.0D, -1.0D);
        poses.mulPose(Axis.YP.rotationDegrees((float) animation.turn.y));
        poses.translate(-1.2D, 0.0D, 1.0D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) animation.roll.z));
        poses.translate(animation.smack.x, animation.smack.y, animation.smack.z);
        poses.scale(0.125F, 0.125F, 0.125F);

        poses.translate(0.0D, -4.0D, -10.0D);
        poses.mulPose(Axis.XP.rotationDegrees((float) animation.equip.x));
        poses.translate(0.0D, 4.0D, 10.0D);
        poses.translate(0.0D, 0.0D, animation.recoil.z);

        render("Rifle", poses, buffers, light, overlay);
        render("Internals", poses, buffers, light, overlay);

        poses.pushPose();
        poses.translate(-2.1875D, -1.75D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) animation.lid.z));
        poses.translate(2.1875D, 1.75D, 0.0D);
        render("Lid", poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(animation.mag.x, animation.mag.y, animation.mag.z);
        render("Magazine", poses, buffers, light, overlay);
        if (animation.bullets.x == 0.0D) render("Bullets", poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, 0.0D, 29.0D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.scale(1.5F, 1.5F, 1.5F);
        WeaponSmokeRenderer.render(stack, 0, poses, buffers, 0.5D,
                WeaponSmokeRenderer.STANDARD,
                HangmanItem.state(stack) == HangmanItem.GunState.RELOADING);
        poses.popPose();
    }

    private void renderStatic(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        for (String group : GROUPS) render(group, poses, buffers, light, overlay);
    }

    private void render(String group, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        mesh().render(group, poses.last(), buffers.getBuffer(RenderType.entityCutout(TEXTURE)),
                1.0F, light, overlay, 0xFFFFFFFF);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) {
            mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(), MODEL, GROUPS, "Hangman");
        }
        return mesh;
    }

    private static void setupContext(ItemDisplayContext context, PoseStack poses) {
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case GUI -> {
                poses.scale(1.0F, -1.0F, 1.0F);
                poses.scale(1.0F, 1.0F, -1.0F);
                poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
                poses.scale(0.375F / 16.0F, 0.375F / 16.0F, 0.375F / 16.0F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
                poses.translate(-0.5D, 2.5D, 0.0D);
            }
            case GROUND, FIXED -> poses.scale(0.0625F, 0.0625F, 0.0625F);
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> setupThirdPerson(context, poses);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                float aim = ClientWeaponEvents.aimingProgress(partial);
                double side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 1.0D : -1.0D;
                poses.mulPose(Axis.YP.rotationDegrees(180.0F));
                poses.translate(0.0D, 0.0D, 0.875D);
                poses.translate(lerp(side * 1.2D, 0.0D, aim), lerp(-0.7D, -0.1875D, aim),
                        lerp(1.4D, 1.25D, aim));
            }
            default -> poses.scale(0.0625F, 0.0625F, 0.0625F);
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
        poses.scale(0.5F, 0.5F, 0.5F);
        poses.translate(0.0D, 4.25D, 11.0D);
    }

    private static Animation animation(ItemStack stack) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        double time = (HangmanItem.animationTimer(stack) + partial) * 50.0D;
        return switch (HangmanItem.animation(stack)) {
            case EQUIP -> new Animation(ZERO,
                    sequence(time, frame(60, 0, 0, 0), frame(0, 0, 0, 500, Curve.SIN_DOWN)),
                    ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
            case CYCLE -> new Animation(
                    sequence(time, frame(0, 0, 0, 50), frame(0, 0, -3, 50), frame(0, 0, 0, 250)),
                    ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
            case RELOAD -> reload(time);
            case INSPECT -> inspect(time);
            case JAMMED -> jammed(time);
            default -> Animation.NONE;
        };
    }

    private static Animation reload(double time) {
        return new Animation(ZERO,
                sequence(time, frame(-15,0,0,500,Curve.SIN_FULL), frame(-15,0,0,850),
                        frame(-25,0,0,100,Curve.SIN_DOWN), frame(0,0,0,350,Curve.SIN_FULL)),
                sequence(time, frame(0,0,0,500), frame(0,0,25,250,Curve.SIN_FULL),
                        frame(0,0,25,1000), frame(0,0,0,250,Curve.SIN_FULL)),
                ZERO, ZERO,
                sequence(time, frame(0,0,-90,250), frame(0,0,-90,1500), frame(0,0,0,250)),
                sequence(time, frame(0,0,0,250), frame(0,-10,0,250,Curve.SIN_UP),
                        frame(0,-10,0,500), frame(0,0,0,350,Curve.SIN_FULL)),
                sequence(time, frame(1,1,1,0), frame(0,0,0,500)));
    }

    private static Animation inspect(double time) {
        return new Animation(ZERO, ZERO,
                sequence(time, frame(0,0,110,500,Curve.SIN_FULL), frame(0,0,110,550),
                        frame(0,0,0,500,Curve.SIN_FULL)),
                sequence(time, frame(0,170,0,500,Curve.SIN_UP), frame(0,170,0,550),
                        frame(0,0,0,500,Curve.SIN_FULL)),
                sequence(time, frame(0,0,0,500), frame(0,0,1,150,Curve.SIN_DOWN),
                        frame(0,0,-3,150,Curve.SIN_UP), frame(0,0,0,350,Curve.SIN_FULL)),
                ZERO, ZERO, ZERO);
    }

    private static Animation jammed(double time) {
        return new Animation(ZERO,
                sequence(time, frame(0,0,0,1000), frame(-10,0,0,100,Curve.SIN_DOWN),
                        frame(0,0,0,350,Curve.SIN_FULL)),
                sequence(time, frame(0,0,0,500), frame(0,0,25,250,Curve.SIN_FULL),
                        frame(0,0,25,300), frame(0,0,0,250,Curve.SIN_FULL)),
                ZERO, ZERO,
                sequence(time, frame(0,0,0,500), frame(0,0,-90,250),
                        frame(0,0,-90,300), frame(0,0,0,250)),
                sequence(time, frame(0,0,0,500), frame(0,0,0,250),
                        frame(0,-3,0,150,Curve.SIN_UP), frame(0,0,0,150,Curve.SIN_FULL)),
                ZERO);
    }

    private static Vec sequence(double time, Frame... frames) {
        double elapsed = 0.0D;
        Vec previous = ZERO;
        for (Frame frame : frames) {
            if (frame.duration <= 0.0D) {
                previous = frame.value;
                continue;
            }
            if (time < elapsed + frame.duration) {
                double progress = Math.max(0.0D, Math.min((time - elapsed) / frame.duration, 1.0D));
                return previous.lerp(frame.value, frame.curve.apply(progress));
            }
            elapsed += frame.duration;
            previous = frame.value;
        }
        return previous;
    }

    private static Frame frame(double x, double y, double z, double duration) {
        return frame(x, y, z, duration, Curve.LINEAR);
    }

    private static Frame frame(double x, double y, double z, double duration, Curve curve) {
        return new Frame(new Vec(x, y, z), duration, curve);
    }

    private static double lerp(double start, double end, float progress) {
        return start + (end - start) * progress;
    }

    private static void renderMuzzleFlash(PoseStack poses, MultiBufferSource buffers,
                                          float progress, float shotRandom) {
        float width = 6.0F * progress;
        float length = 7.5F * progress;
        float inset = 2.0F;
        poses.pushPose();
        poses.translate(0.0D, 0.0D, 29.0D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(90.0F * shotRandom));
        poses.scale(2.0F, 2.0F, 2.0F);
        VertexConsumer consumer = buffers.getBuffer(SednaMuzzleFlash.TYPE);
        PoseStack.Pose pose = poses.last();

        flashVertex(consumer, pose, 0, -width, -inset, 1, 1);
        flashVertex(consumer, pose, 0, width, -inset, 0, 1);
        flashVertex(consumer, pose, 0.1F, width, length - inset, 0, 0);
        flashVertex(consumer, pose, 0.1F, -width, length - inset, 1, 0);

        flashVertex(consumer, pose, 0, width, inset, 0, 1);
        flashVertex(consumer, pose, 0, -width, inset, 1, 1);
        flashVertex(consumer, pose, 0.1F, -width, -length + inset, 1, 0);
        flashVertex(consumer, pose, 0.1F, width, -length + inset, 0, 0);

        flashVertex(consumer, pose, 0, -inset, width, 0, 1);
        flashVertex(consumer, pose, 0, -inset, -width, 1, 1);
        flashVertex(consumer, pose, 0.1F, length - inset, -width, 1, 0);
        flashVertex(consumer, pose, 0.1F, length - inset, width, 0, 0);

        flashVertex(consumer, pose, 0, inset, -width, 1, 1);
        flashVertex(consumer, pose, 0, inset, width, 0, 1);
        flashVertex(consumer, pose, 0.1F, -length + inset, width, 0, 0);
        flashVertex(consumer, pose, 0.1F, -length + inset, -width, 1, 0);
        poses.popPose();
    }

    private static void flashVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                    float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static final Vec ZERO = new Vec(0, 0, 0);
    private record Vec(double x, double y, double z) {
        Vec lerp(Vec other, double progress) {
            return new Vec(x + (other.x - x) * progress, y + (other.y - y) * progress,
                    z + (other.z - z) * progress);
        }
    }
    private record Frame(Vec value, double duration, Curve curve) { }
    private enum Curve {
        LINEAR { double apply(double x) { return x; } },
        SIN_UP { double apply(double x) { return 1.0D - Math.cos(x * Math.PI * 0.5D); } },
        SIN_DOWN { double apply(double x) { return Math.sin(x * Math.PI * 0.5D); } },
        SIN_FULL { double apply(double x) { return (-Math.cos(x * Math.PI) + 1.0D) * 0.5D; } };
        abstract double apply(double x);
    }
    private record Animation(Vec recoil, Vec equip, Vec roll, Vec turn, Vec smack,
                             Vec lid, Vec mag, Vec bullets) {
        private static final Animation NONE = new Animation(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }
}
