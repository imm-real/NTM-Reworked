package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.item.HenryRifleItem;
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

/** Henry renderer and its lever-operated animation railway. */
public final class HenryItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation GUN = model("henry_gun");
    public static final ModelResourceLocation SIGHT = model("henry_sight");
    public static final ModelResourceLocation HAMMER = model("henry_hammer");
    public static final ModelResourceLocation LEVER = model("henry_lever");
    public static final ModelResourceLocation FRONT = model("henry_front");
    public static final ModelResourceLocation BULLET = model("henry_bullet");
    public static final ModelResourceLocation GUN_LINCOLN = model("henry_gun_lincoln");
    public static final ModelResourceLocation SIGHT_LINCOLN = model("henry_sight_lincoln");
    public static final ModelResourceLocation HAMMER_LINCOLN = model("henry_hammer_lincoln");
    public static final ModelResourceLocation LEVER_LINCOLN = model("henry_lever_lincoln");
    public static final ModelResourceLocation FRONT_LINCOLN = model("henry_front_lincoln");
    public static final ModelResourceLocation BULLET_LINCOLN = model("henry_bullet_lincoln");

    public HenryItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof HenryRifleItem gun)) return;
        boolean lincoln = gun.isLincoln();
        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        poses.pushPose();
        setupContext(context, poses);
        if (firstPerson) renderFirstPerson(stack, lincoln, poses, buffers, packedLight, packedOverlay);
        else renderStatic(lincoln, poses, buffers, packedLight, packedOverlay);

        boolean held = firstPerson || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        long elapsed = System.currentTimeMillis() - ClientWeaponEvents.lastShot(stack);
        if (held && elapsed >= 0L && elapsed < 75L) {
            renderMuzzleFlash(poses, buffers, elapsed / 75.0F);
        }
        poses.popPose();
    }

    private static void renderFirstPerson(ItemStack stack, boolean lincoln, PoseStack poses,
                                          MultiBufferSource buffers, int light, int overlay) {
        Animation animation = animation(stack);

        poses.translate(animation.recoil.x * 2.0D, animation.recoil.y, animation.recoil.z);
        poses.mulPose(Axis.XP.rotationDegrees((float) (animation.recoil.z * 5.0D)));
        poses.mulPose(Axis.ZP.rotationDegrees((float) animation.turn.z));
        poses.translate(animation.yeet.x, animation.yeet.y, animation.yeet.z);

        poses.translate(0.0D, 1.0D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) animation.roll.z));
        poses.translate(0.0D, -1.0D, 0.0D);

        poses.translate(0.0D, -4.0D, 4.0D);
        poses.mulPose(Axis.XP.rotationDegrees((float) animation.lift.x));
        poses.translate(0.0D, 4.0D, -4.0D);

        poses.translate(0.0D, 2.0D, -4.0D);
        poses.mulPose(Axis.XN.rotationDegrees((float) animation.equip.x));
        poses.translate(0.0D, -2.0D, 4.0D);

        renderModel(part(lincoln, GUN, GUN_LINCOLN), poses, buffers, light, overlay);

        poses.pushPose();
        poses.translate(0.0D, 1.25D, -0.1875D);
        poses.mulPose(Axis.XP.rotationDegrees((float) animation.sight.x));
        poses.translate(0.0D, -1.25D, 0.1875D);
        renderModel(part(lincoln, SIGHT, SIGHT_LINCOLN), poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, 0.625D, -3.0D);
        poses.mulPose(Axis.XP.rotationDegrees((float) (-30.0D + animation.hammer.x)));
        poses.translate(0.0D, -0.625D, 3.0D);
        renderModel(part(lincoln, HAMMER, HAMMER_LINCOLN), poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, 0.25D, -2.3125D);
        poses.mulPose(Axis.XP.rotationDegrees((float) animation.lever.x));
        poses.translate(0.0D, -0.25D, 2.3125D);
        renderModel(part(lincoln, LEVER, LEVER_LINCOLN), poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, 1.0D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) animation.twist.z));
        poses.translate(0.0D, -1.0D, 0.0D);
        renderModel(part(lincoln, FRONT, FRONT_LINCOLN), poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(animation.bullet.x, animation.bullet.y, animation.bullet.z - 1.0D);
        renderModel(part(lincoln, BULLET, BULLET_LINCOLN), poses, buffers, light, overlay);
        poses.popPose();
    }

    private static void renderStatic(boolean lincoln, PoseStack poses, MultiBufferSource buffers,
                                     int light, int overlay) {
        renderModel(part(lincoln, GUN, GUN_LINCOLN), poses, buffers, light, overlay);
        renderModel(part(lincoln, SIGHT, SIGHT_LINCOLN), poses, buffers, light, overlay);
        renderModel(part(lincoln, HAMMER, HAMMER_LINCOLN), poses, buffers, light, overlay);
        renderModel(part(lincoln, LEVER, LEVER_LINCOLN), poses, buffers, light, overlay);
        renderModel(part(lincoln, FRONT, FRONT_LINCOLN), poses, buffers, light, overlay);
        renderModel(part(lincoln, BULLET, BULLET_LINCOLN), poses, buffers, light, overlay);
    }

    private static ModelResourceLocation part(boolean lincoln, ModelResourceLocation normal,
                                              ModelResourceLocation lincolnModel) {
        return lincoln ? lincolnModel : normal;
    }

    private static void setupContext(ItemDisplayContext context, PoseStack poses) {
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case GUI -> {
                // GUI adds a Y reflection that the Henry camera never asked for. Cancel it.
                poses.scale(1.0F, -1.0F, 1.0F);
                poses.scale(1.0F, 1.0F, -1.0F);
                poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
                poses.scale(1.5F / 16.0F, 1.5F / 16.0F, 1.5F / 16.0F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
                poses.translate(-0.5D, 0.5D, 0.0D);
            }
            case GROUND -> {
                poses.scale(0.125F, 0.125F, 0.125F);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
            case FIXED -> {
                poses.scale(0.075F, 0.075F, 0.075F);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> setupThirdPerson(context, poses);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                float aim = ClientWeaponEvents.aimingProgress(partial);
                double side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 1.0D : -1.0D;
                poses.mulPose(Axis.YP.rotationDegrees(180.0F));
                poses.translate(0.0D, 0.0D, 0.875D);
                poses.translate(lerp(side, 0.0D, aim), lerp(-0.8D, -0.625D, aim),
                        lerp(1.4D, 1.0D, aim));
                poses.mulPose(Axis.XN.rotationDegrees(2.5F * aim));
                poses.scale(0.375F, 0.375F, 0.375F);
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
        poses.scale(1.75F, 1.75F, 1.75F);
        poses.translate(0.0D, 0.25D, 3.0D);
    }

    private static Animation animation(ItemStack stack) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        double time = (HenryRifleItem.animationTimer(stack) + partial) * 50.0D;
        return switch (HenryRifleItem.animation(stack)) {
            case EQUIP -> new Animation(ZERO,
                    sequence(time, frame(-90, 0, 0, 0), frame(0, 0, -3, 350, Curve.SIN_DOWN)),
                    sequence(time, frame(80, 0, 0, 0), frame(80, 0, 0, 500),
                            frame(0, 0, -3, 250, Curve.SIN_DOWN)),
                    ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
            case CYCLE -> new Animation(
                    sequence(time, frame(0, 0, 0, 50), frame(0, 0, -1, 50), frame(0, 0, 0, 250)),
                    ZERO,
                    sequence(time, frame(35, 0, 0, 100, Curve.SIN_DOWN),
                            frame(0, 0, 0, 100, Curve.SIN_FULL)),
                    sequence(time, frame(30, 0, 0, 50), frame(30, 0, 0, 550), frame(0, 0, 0, 200)),
                    sequence(time, frame(0, 0, 0, 600), frame(-90, 0, 0, 200), frame(0, 0, 0, 200)),
                    sequence(time, frame(0, 0, 0, 600), frame(0, 0, 45, 200, Curve.SIN_DOWN),
                            frame(0, 0, 0, 200, Curve.SIN_UP)),
                    ZERO, ZERO, ZERO, ZERO, ZERO);
            case CYCLE_DRY -> new Animation(ZERO, ZERO, ZERO,
                    sequence(time, frame(30, 0, 0, 50), frame(30, 0, 0, 550), frame(0, 0, 0, 200)),
                    sequence(time, frame(0, 0, 0, 600), frame(-90, 0, 0, 200), frame(0, 0, 0, 200)),
                    sequence(time, frame(0, 0, 0, 600), frame(0, 0, 45, 200, Curve.SIN_DOWN),
                            frame(0, 0, 0, 200, Curve.SIN_UP)),
                    ZERO, ZERO, ZERO, ZERO, ZERO);
            case RELOAD -> reload(time);
            case RELOAD_CYCLE -> reloadCycle(time);
            case RELOAD_END -> reloadEnd(time, HenryRifleItem.amountBeforeReload(stack) <= 0);
            case JAMMED -> jammed(time);
            case INSPECT -> new Animation(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO,
                    sequence(time, frame(0, 2, 0, 200, Curve.SIN_DOWN),
                            frame(0, 0, 0, 200, Curve.SIN_UP)),
                    sequence(time, frame(0, 0, 360, 400)));
            default -> Animation.NONE;
        };
    }

    private static Animation reload(double time) {
        return new Animation(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO,
                sequence(time, frame(-60, 0, 0, 400, Curve.SIN_FULL)),
                sequence(time, frame(0, 0, 0, 500), frame(0, 0, -90, 200, Curve.SIN_FULL)),
                sequence(time, frame(0, 0, 0, 700), frame(3, 0, -6, 0),
                        frame(0, 0, 1, 300, Curve.SIN_FULL), frame(0, 0, 0, 250, Curve.SIN_FULL)),
                ZERO, ZERO);
    }

    private static Animation reloadCycle(double time) {
        return new Animation(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO,
                sequence(time, frame(-60, 0, 0, 0)),
                sequence(time, frame(0, 0, -90, 0)),
                sequence(time, frame(3, 0, -6, 0), frame(0, 0, 1, 300, Curve.SIN_FULL),
                        frame(0, 0, 0, 250, Curve.SIN_FULL)),
                ZERO, ZERO);
    }

    private static Animation reloadEnd(double time, boolean empty) {
        return new Animation(ZERO, ZERO, ZERO, ZERO,
                sequence(time, frame(0, 0, 0, 700), frame(empty ? -90 : 0, 0, 0, 200),
                        frame(0, 0, 0, 200)),
                sequence(time, frame(0, 0, 0, 700), frame(0, 0, empty ? 45 : 0, 200, Curve.SIN_DOWN),
                        frame(0, 0, 0, 200, Curve.SIN_UP)),
                sequence(time, frame(-60, 0, 0, 0), frame(-60, 0, 0, 300),
                        frame(0, 0, 0, 400, Curve.SIN_FULL)),
                sequence(time, frame(0, 0, -90, 0), frame(0, 0, 0, 200, Curve.SIN_FULL)),
                ZERO, ZERO, ZERO);
    }

    private static Animation jammed(double time) {
        return new Animation(ZERO, ZERO, ZERO, ZERO,
                sequence(time, frame(0, 0, 0, 700), frame(-90, 0, 0, 200), frame(0, 0, 0, 200),
                        frame(0, 0, 0, 500), frame(-90, 0, 0, 200), frame(0, 0, 0, 200),
                        frame(0, 0, 0, 200), frame(-90, 0, 0, 200), frame(0, 0, 0, 200)),
                sequence(time, frame(0, 0, 0, 700), frame(0, 0, 45, 200, Curve.SIN_DOWN),
                        frame(0, 0, 0, 200, Curve.SIN_UP), frame(0, 0, 0, 500),
                        frame(0, 0, 45, 200, Curve.SIN_FULL), frame(0, 0, 45, 600),
                        frame(0, 0, 0, 200, Curve.SIN_FULL)),
                sequence(time, frame(-60, 0, 0, 0), frame(-60, 0, 0, 300),
                        frame(0, 0, 0, 400, Curve.SIN_FULL)),
                sequence(time, frame(0, 0, -90, 0), frame(0, 0, 0, 200, Curve.SIN_FULL)),
                ZERO, ZERO, ZERO);
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
        poses.translate(0.0D, 1.0D, 8.0D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
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

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "item/" + path));
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
    private record Animation(Vec recoil, Vec equip, Vec sight, Vec hammer, Vec lever, Vec turn,
                             Vec lift, Vec twist, Vec bullet, Vec yeet, Vec roll) {
        private static final Animation NONE = new Animation(
                ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }
}
