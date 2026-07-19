package com.hbm.ntm.client.render;

import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.item.DualMaresLegItem;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Two Mareslegs. Stock and barrel sold separately. */
public final class DualMaresLegItemRenderer extends BlockEntityWithoutLevelRenderer {

    public DualMaresLegItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof DualMaresLegItem)) return;

        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND ->
                    renderFirstPerson(stack, poses, buffers, packedLight, packedOverlay);
            case THIRD_PERSON_RIGHT_HAND -> {
                // Right hand gets config one because zero was busy.
                setupThirdPerson(context, poses);
                renderStatic(poses, buffers, packedLight, packedOverlay);
                renderHeldFlash(stack, 1, poses, buffers);
            }
            case THIRD_PERSON_LEFT_HAND -> {
                // Left hand gets config zero and no explanation.
                setupThirdPerson(context, poses);
                renderStatic(poses, buffers, packedLight, packedOverlay);
                renderHeldFlash(stack, 0, poses, buffers);
            }
            case GUI -> renderInventory(poses, buffers, packedLight, packedOverlay);
            default -> renderDropped(poses, buffers, packedLight, packedOverlay);
        }
        poses.popPose();
    }

    private static void renderFirstPerson(ItemStack stack, PoseStack poses,
                                          MultiBufferSource buffers, int light, int overlay) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float aim = ClientWeaponEvents.aimingProgress(partial);

        poses.mulPose(Axis.YP.rotationDegrees(180.0F));
        // GL11's ghost demands 0.875 Z.
        poses.translate(0.0D, 0.0D, 0.875D);

        for (int index = 0; index < DualMaresLegItem.RECEIVER_COUNT; index++) {
            int i = index == 0 ? -1 : 1;
            poses.pushPose();
            // Both guns attempt to share the sights.
            poses.translate(lerp(-1.2D * i, 0.0D, aim), lerp(-0.8D, -0.484375D, aim),
                    lerp(1.6D, 1.0D, aim));
            poses.scale(0.375F, 0.375F, 0.375F);
            renderAnimated(stack, index, poses, buffers, light, overlay);
            poses.popPose();
        }
    }

    private static void renderAnimated(ItemStack stack, int index, PoseStack poses,
                                       MultiBufferSource buffers, int light, int overlay) {
        Animation animation = animation(stack, index);

        poses.translate(animation.recoil.x * 2.0D, animation.recoil.y, animation.recoil.z);
        poses.mulPose(Axis.XP.rotationDegrees((float) (animation.recoil.z * 5.0D)));
        poses.mulPose(Axis.ZP.rotationDegrees((float) animation.turn.z));

        poses.translate(0.0D, 0.0D, -4.0D);
        poses.mulPose(Axis.XP.rotationDegrees((float) animation.lift.x));
        poses.translate(0.0D, 0.0D, 4.0D);

        poses.translate(0.0D, 0.0D, -4.0D);
        poses.mulPose(Axis.XN.rotationDegrees((float) animation.equip.x));
        poses.translate(0.0D, 0.0D, 4.0D);

        poses.translate(0.0D, 0.0D, -2.0D);
        poses.mulPose(Axis.XN.rotationDegrees((float) animation.flip.x));
        poses.translate(0.0D, 0.0D, 2.0D);

        renderModel(MaresLegItemRenderer.GUN, poses, buffers, light, overlay);

        poses.pushPose();
        poses.translate(0.0D, 0.125D, -2.875D);
        poses.mulPose(Axis.XP.rotationDegrees((float) animation.lever.x));
        poses.translate(0.0D, -0.125D, 2.875D);
        renderModel(MaresLegItemRenderer.LEVER, poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(animation.shell.x, animation.shell.y - 0.75D, animation.shell.z);
        renderModel(MaresLegItemRenderer.SHELL, poses, buffers, light, overlay);
        poses.popPose();

        if (animation.flag.x != 0.0D) {
            poses.pushPose();
            poses.translate(0.0D, -0.5D, 0.0D);
            renderModel(MaresLegItemRenderer.SHELL, poses, buffers, light, overlay);
            poses.popPose();
        }

        renderHeldFlash(stack, index, poses, buffers);
    }

    private static void renderStatic(PoseStack poses, MultiBufferSource buffers,
                                     int light, int overlay) {
        // Static views hide the shell to improve its self-esteem.
        renderModel(MaresLegItemRenderer.GUN, poses, buffers, light, overlay);
        renderModel(MaresLegItemRenderer.LEVER, poses, buffers, light, overlay);
    }

    private static void renderInventory(PoseStack poses, MultiBufferSource buffers,
                                        int light, int overlay) {
        // Unflip the GUI, then squeeze two rifles into one slot.
        poses.scale(1.0F, -1.0F, 1.0F);
        poses.scale(1.0F, 1.0F, -1.0F);
        poses.scale(2.5F / 16.0F, 2.5F / 16.0F, 2.5F / 16.0F);

        poses.pushPose();
        poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(25.0F));
        poses.mulPose(Axis.YP.rotationDegrees(45.0F));
        poses.translate(-1.0D, 0.0D, 0.0D);
        renderStatic(poses, buffers, light, overlay);
        poses.popPose();

        poses.translate(0.0D, 0.0D, 5.0D);
        poses.pushPose();
        poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
        poses.mulPose(Axis.YN.rotationDegrees(90.0F));
        poses.mulPose(Axis.XN.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(25.0F));
        poses.mulPose(Axis.YN.rotationDegrees(45.0F));
        poses.translate(1.0D, 0.0D, 0.0D);
        renderStatic(poses, buffers, light, overlay);
        poses.popPose();
    }

    private static void renderDropped(PoseStack poses, MultiBufferSource buffers,
                                      int light, int overlay) {
        poses.scale(0.125F, 0.125F, 0.125F);
        poses.mulPose(Axis.YN.rotationDegrees(90.0F));

        // Dropped guns maintain social distancing.
        poses.pushPose();
        poses.translate(-1.0D, 1.0D, 0.0D);
        renderStatic(poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(1.0D, 1.0D, 0.0D);
        renderStatic(poses, buffers, light, overlay);
        poses.popPose();
    }

    private static void setupThirdPerson(ItemDisplayContext context, PoseStack poses) {
        boolean left = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        float side = left ? -1.0F : 1.0F;
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
        // Left and right hands disagree on personal space.
        poses.scale(0.125F, 0.125F, 0.125F);
        poses.mulPose(Axis.ZP.rotationDegrees(side * 15.0F));
        poses.mulPose(Axis.YP.rotationDegrees(side * 12.5F));
        poses.mulPose(Axis.XP.rotationDegrees(left ? 10.0F : 15.0F));
        poses.translate(side * (left ? 5.0D : 3.5D), 0.0D, 0.0D);
        // Make both guns 75% more irresponsible.
        poses.scale(1.75F, 1.75F, 1.75F);
        poses.translate(0.0D, 0.25D, 3.0D);
    }

    private static Animation animation(ItemStack stack, int index) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        double time = (DualMaresLegItem.animationTimer(stack, index) + partial) * 50.0D;
        return switch (DualMaresLegItem.animation(stack, index)) {
            case EQUIP -> new Animation(ZERO,
                    sequence(time, frame(-60, 0, 0, 0), frame(0, 0, -3, 250, Curve.SIN_DOWN)),
                    ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
            case CYCLE -> new Animation(
                    sequence(time, frame(0, 0, 0, 50), frame(0, 0, -1, 50), frame(0, 0, 0, 250)),
                    ZERO,
                    sequence(time, frame(0, 0, 0, 600), frame(-85, 0, 0, 200), frame(0, 0, 0, 200)),
                    ZERO,
                    sequence(time, frame(0, 0, 0, 600), frame(360, 0, 0, 400)),
                    ZERO,
                    sequence(time, frame(-20, 0, 0, 0)),
                    ZERO);
            case CYCLE_DRY -> new Animation(ZERO, ZERO,
                    sequence(time, frame(0, 0, 0, 600), frame(-90, 0, 0, 200), frame(0, 0, 0, 200)),
                    ZERO,
                    sequence(time, frame(0, 0, 0, 600), frame(360, 0, 0, 400)),
                    ZERO,
                    sequence(time, frame(-20, 0, 0, 0)),
                    ZERO);
            case JAMMED -> new Animation(ZERO, ZERO,
                    sequence(time, frame(-85, 0, 0, 0), frame(-15, 0, 0, 200),
                            frame(-15, 0, 0, 650), frame(-85, 0, 0, 200),
                            frame(-15, 0, 0, 200), frame(-15, 0, 0, 200),
                            frame(-85, 0, 0, 200), frame(0, 0, 0, 200)),
                    ZERO, ZERO,
                    sequence(time, frame(30, 0, 0, 0), frame(30, 0, 0, 250),
                            frame(0, 0, 0, 400, Curve.SIN_FULL)),
                    ZERO, sequence(time, frame(1, 1, 1, 0)));
            // Short animation falls through and borrows the long one's homework.
            case RELOAD -> reload(time, DualMaresLegItem.rounds(stack, 0) <= 0);
            case RELOAD_CYCLE -> reloadCycle(time);
            case RELOAD_END -> reloadEnd(time);
            case INSPECT -> new Animation(ZERO, ZERO, ZERO,
                    sequence(time, frame(0, 0, 0, 450), frame(0, 0, -90, 500, Curve.SIN_FULL),
                            frame(0, 0, -90, 500), frame(0, 0, 0, 500, Curve.SIN_FULL)),
                    ZERO,
                    sequence(time, frame(-35, 0, 0, 300, Curve.SIN_FULL),
                            frame(-35, 0, 0, 1150), frame(0, 0, 0, 500, Curve.SIN_FULL)),
                    ZERO, ZERO);
            default -> Animation.NONE;
        };
    }

    private static Animation reload(double time, boolean empty) {
        Vec flag = empty
                ? sequence(time, frame(0, 0, 0, 900), frame(1, 1, 1, 0))
                : sequence(time, frame(1, 1, 1, 0));
        return new Animation(ZERO, ZERO,
                sequence(time, frame(0, 0, 0, 400), frame(-85, 0, 0, 200)),
                ZERO, ZERO,
                sequence(time, frame(30, 0, 0, 400, Curve.SIN_FULL)),
                sequence(time, frame(0, 0, 0, 600), frame(0, 0.25, -3, 0),
                        frame(0, empty ? 0.25 : 0.125, -1.5, 150, Curve.SIN_UP),
                        frame(0, empty ? 0.25 : -0.25, 0, 150, Curve.SIN_DOWN)),
                flag);
    }

    private static Animation reloadCycle(double time) {
        return new Animation(ZERO, ZERO, sequence(time, frame(-85, 0, 0, 0)), ZERO, ZERO,
                sequence(time, frame(30, 0, 0, 0)),
                sequence(time, frame(0, 0.25, -3, 0),
                        frame(0, 0.125, -1.5, 150, Curve.SIN_UP),
                        frame(0, -0.125, 0, 150, Curve.SIN_DOWN)),
                sequence(time, frame(1, 1, 1, 0)));
    }

    private static Animation reloadEnd(double time) {
        return new Animation(ZERO, ZERO,
                sequence(time, frame(-85, 0, 0, 0), frame(0, 0, 0, 200)), ZERO, ZERO,
                sequence(time, frame(30, 0, 0, 0), frame(30, 0, 0, 250),
                        frame(0, 0, 0, 400, Curve.SIN_FULL)),
                ZERO, sequence(time, frame(1, 1, 1, 0)));
    }

    private static void renderHeldFlash(ItemStack stack, int index, PoseStack poses,
                                        MultiBufferSource buffers) {
        long shot = ClientWeaponEvents.lastShot(stack, index);
        long elapsed = System.currentTimeMillis() - shot;
        if (elapsed < 0L || elapsed >= 75L) return;
        float progress = elapsed / 75.0F;
        poses.pushPose();
        poses.translate(0.0D, 1.0D, 3.75D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(90.0F * ClientWeaponEvents.shotRandom(stack, index)));
        SednaMuzzleFlash.render(poses, buffers, progress, 5.0F);
        poses.popPose();
    }

    private static void flashQuad(VertexConsumer consumer, PoseStack.Pose pose,
                                  float width, float length, boolean vertical) {
        flashVertex(consumer, pose, vertical ? 0 : -width, vertical ? -width : 0, 0, 0, 1);
        flashVertex(consumer, pose, vertical ? 0 : width, vertical ? width : 0, 0, 1, 1);
        flashVertex(consumer, pose, vertical ? 0 : width, vertical ? width : 0, length, 1, 0);
        flashVertex(consumer, pose, vertical ? 0 : -width, vertical ? -width : 0, length, 0, 0);
    }

    private static void flashVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                    float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
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
                double progress = Math.max(0.0D,
                        Math.min((time - elapsed) / frame.duration, 1.0D));
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

    private static void renderModel(ModelResourceLocation location, PoseStack poses,
                                    MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(location);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        renderer.renderModel(poses.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(),
                model, 1.0F, 1.0F, 1.0F, light, overlay);
    }

    private static final Vec ZERO = new Vec(0, 0, 0);

    private record Vec(double x, double y, double z) {
        Vec lerp(Vec other, double progress) {
            return new Vec(x + (other.x - x) * progress,
                    y + (other.y - y) * progress,
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

    /** Recoil, equip, lever, turn, flip, lift, shell and one mysterious flag. */
    private record Animation(Vec recoil, Vec equip, Vec lever, Vec turn,
                             Vec flip, Vec lift, Vec shell, Vec flag) {
        private static final Animation NONE = new Animation(
                ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }
}
