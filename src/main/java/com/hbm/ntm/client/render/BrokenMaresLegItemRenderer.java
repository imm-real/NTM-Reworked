package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.item.BrokenMaresLegItem;
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

/**
 * ItemRenderMaresleg with {@code getShort(stack) == true} for gun_maresleg_broken:
 * a single gun that uses the SHORT positioning (shortened Gun+Lever, FLIP bus,
 * muzzle/smoke z = 3.75) and the distinct broken texture. It combines
 * DualMaresLegItemRenderer's SHORT animation body with MaresLegItemRenderer's
 * single-gun third-person and inventory transforms.
 */
public final class BrokenMaresLegItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation GUN_BROKEN = model("maresleg_broken_gun");
    public static final ModelResourceLocation LEVER_BROKEN = model("maresleg_broken_lever");
    public static final ModelResourceLocation SHELL_BROKEN = model("maresleg_broken_shell");

    public BrokenMaresLegItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof BrokenMaresLegItem)) return;

        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND ->
                    renderFirstPerson(stack, context, poses, buffers, packedLight, packedOverlay);
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                setupThirdPerson(context, poses);
                renderStatic(poses, buffers, packedLight, packedOverlay);
                renderHeldFlash(stack, poses, buffers);
            }
            case GUI -> renderInventory(poses, buffers, packedLight, packedOverlay);
            default -> renderDropped(poses, buffers, packedLight, packedOverlay);
        }
        poses.popPose();
    }

    private static void renderFirstPerson(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                          MultiBufferSource buffers, int light, int overlay) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float aim = ClientWeaponEvents.aimingProgress(partial);
        double side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 1.0D : -1.0D;

        poses.mulPose(Axis.YP.rotationDegrees(180.0F));
        // setupFirstPerson: GL11.glTranslated(0, 0, 0.875).
        poses.translate(0.0D, 0.0D, 0.875D);
        // standardAimingTransform(stack, -1.25*0.8*side, -1*0.8, 2*0.8, 0, -3.875/8, 1).
        poses.translate(lerp(side, 0.0D, aim), lerp(-0.8D, -0.484375D, aim), lerp(1.6D, 1.0D, aim));
        poses.scale(0.375F, 0.375F, 0.375F);
        renderAnimated(stack, poses, buffers, light, overlay);
    }

    private static void renderAnimated(ItemStack stack, PoseStack poses,
                                       MultiBufferSource buffers, int light, int overlay) {
        Animation animation = animation(stack);

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

        renderModel(GUN_BROKEN, poses, buffers, light, overlay);

        poses.pushPose();
        poses.translate(0.0D, 0.125D, -2.875D);
        poses.mulPose(Axis.XP.rotationDegrees((float) animation.lever.x));
        poses.translate(0.0D, -0.125D, 2.875D);
        renderModel(LEVER_BROKEN, poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(animation.shell.x, animation.shell.y - 0.75D, animation.shell.z);
        renderModel(SHELL_BROKEN, poses, buffers, light, overlay);
        poses.popPose();

        if (animation.flag.x != 0.0D) {
            poses.pushPose();
            poses.translate(0.0D, -0.5D, 0.0D);
            renderModel(SHELL_BROKEN, poses, buffers, light, overlay);
            poses.popPose();
        }

        renderHeldFlash(stack, poses, buffers);
    }

    private static void renderStatic(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        // renderOther: only the Gun and Lever groups; static views omit the Shell.
        renderModel(GUN_BROKEN, poses, buffers, light, overlay);
        renderModel(LEVER_BROKEN, poses, buffers, light, overlay);
    }

    private static void renderInventory(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        // Short inventory branch: cancel GUI Y, flip Z, then perform the ZP225/YP90 ritual.
        poses.scale(1.0F, -1.0F, 1.0F);
        poses.scale(1.0F, 1.0F, -1.0F);
        poses.scale(2.5F / 16.0F, 2.5F / 16.0F, 2.5F / 16.0F);
        poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(25.0F));
        poses.mulPose(Axis.YP.rotationDegrees(45.0F));
        poses.translate(-1.0D, 0.0D, 0.0D);
        renderStatic(poses, buffers, light, overlay);
    }

    private static void renderDropped(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        // setupEntity: scale 0.125, rotate -90 about Y; a single Gun+Lever.
        poses.scale(0.125F, 0.125F, 0.125F);
        poses.mulPose(Axis.YN.rotationDegrees(90.0F));
        renderStatic(poses, buffers, light, overlay);
    }

    private static void setupThirdPerson(ItemDisplayContext context, PoseStack poses) {
        // Single-gun setupThirdPerson: X 15 / offset 3.5 (not the akimbo X10/offset5 variant).
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
        double time = (BrokenMaresLegItem.animationTimer(stack) + partial) * 50.0D;
        return switch (BrokenMaresLegItem.animation(stack)) {
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
            // LAMBDA_MARESLEG_SHORT_ANIMS falls through to LAMBDA_MARESLEG_ANIMS below.
            case RELOAD -> reload(time, BrokenMaresLegItem.amountBeforeReload(stack) <= 0);
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

    private static void renderHeldFlash(ItemStack stack, PoseStack poses, MultiBufferSource buffers) {
        long shot = ClientWeaponEvents.lastShot(stack, 0);
        long elapsed = System.currentTimeMillis() - shot;
        if (elapsed < 0L || elapsed >= 75L) return;
        float progress = elapsed / 75.0F;
        poses.pushPose();
        poses.translate(0.0D, 1.0D, 3.75D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(90.0F * ClientWeaponEvents.shotRandom(stack, 0)));
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

    private static void renderModel(ModelResourceLocation location, PoseStack poses,
                                    MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(location);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        renderer.renderModel(poses.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(),
                model, 1.0F, 1.0F, 1.0F, light, overlay);
    }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "item/" + path));
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

    /** Buses consumed by ItemRenderMaresleg (short): RECOIL, EQUIP, LEVER, TURN, FLIP, LIFT, SHELL, FLAG. */
    private record Animation(Vec recoil, Vec equip, Vec lever, Vec turn,
                             Vec flip, Vec lift, Vec shell, Vec flag) {
        private static final Animation NONE = new Animation(
                ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }
}
