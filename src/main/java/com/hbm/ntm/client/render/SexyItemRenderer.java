package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.item.SexyItem;
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

/** Legendary Sexy renderer. Whiskey is an animation dependency. */
public final class SexyItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation GUN = model("sexy_gun");
    public static final ModelResourceLocation BARREL = model("sexy_barrel");
    public static final ModelResourceLocation RECOIL_SPRING = model("sexy_recoilspring");
    public static final ModelResourceLocation HOOD = model("sexy_hood");
    public static final ModelResourceLocation LEVER = model("sexy_lever");
    public static final ModelResourceLocation LOCK_SPRING = model("sexy_lockspring");
    public static final ModelResourceLocation MAGAZINE = model("sexy_magazine");
    public static final ModelResourceLocation SHELL = model("sexy_shell");
    public static final ModelResourceLocation BELT = model("sexy_belt");
    public static final ModelResourceLocation WHISKEY = model("whiskey_plane");

    private static final double[] ANGLES_LOADED = {0, 0, 20, 20, 50, 60, 70};
    private static final double[] ANGLES_UNLOADED = {0, -10, -50, -60, -60, 0, 0};
    private static final double RECOIL_SPRING_SCALE = 0.457247371D;

    public SexyItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof SexyItem)) return;
        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        poses.pushPose();
        setupContext(context, poses);
        if (firstPerson) renderFirstPerson(stack, poses, buffers, packedLight, packedOverlay);
        else renderStatic(poses, buffers, packedLight, packedOverlay);

        boolean held = firstPerson || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        long shot = ClientWeaponEvents.lastShot(stack);
        long elapsed = System.currentTimeMillis() - shot;
        if (held && elapsed >= 0L && elapsed < 150L) {
            renderMuzzleFlash(poses, buffers, elapsed / 150.0F, ClientWeaponEvents.shotRandom(stack));
        }
        poses.popPose();
    }

    private void renderFirstPerson(ItemStack stack, PoseStack poses,
                                   MultiBufferSource buffers, int light, int overlay) {
        Animation a = animation(stack);

        // Inspect responsibly.
        if (a.girldinner) {
            poses.pushPose();
            poses.translate(a.bottle.x, a.bottle.y, a.bottle.z);
            poses.translate(0.0D, 2.0D, 0.0D);
            poses.mulPose(Axis.XP.rotationDegrees((float) a.sippy.x));
            poses.mulPose(Axis.YP.rotationDegrees(90.0F));
            poses.mulPose(Axis.XN.rotationDegrees(15.0F));
            poses.translate(0.0D, -2.0D, 0.0D);
            poses.scale(1.5F, 1.5F, 1.5F);
            renderModel(WHISKEY, poses, buffers, light, overlay);
            poses.popPose();
        }

        // Do not pop this; the muzzle flash lives here too.
        poses.translate(0.0D, -1.0D, -8.0D);
        poses.mulPose(Axis.XP.rotationDegrees((float) a.equip.x));
        poses.translate(0.0D, 1.0D, 8.0D);

        poses.translate(0.0D, 0.0D, -6.0D);
        poses.mulPose(Axis.XP.rotationDegrees((float) a.lower.x));
        poses.translate(0.0D, 0.0D, 6.0D);

        poses.translate(0.0D, 0.0D, a.recoil.z);

        renderModel(GUN, poses, buffers, light, overlay);

        poses.pushPose();
        poses.translate(0.0D, 0.0D, a.barrel.z);
        renderModel(BARREL, poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, 0.0D, -0.375D);
        poses.scale(1.0F, 1.0F, (float) (1.0D + RECOIL_SPRING_SCALE * a.barrel.z));
        poses.translate(0.0D, 0.0D, 0.375D);
        renderModel(RECOIL_SPRING, poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, 0.4375D, -2.875D);
        poses.mulPose(Axis.XP.rotationDegrees((float) a.hood.x));
        poses.translate(0.0D, -0.4375D, 2.875D);
        renderModel(HOOD, poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, 0.46875D, -6.875D);
        poses.mulPose(Axis.XP.rotationDegrees((float) (a.lever.z * 60.0D)));
        poses.translate(0.0D, -0.46875D, 6.875D);
        renderModel(LEVER, poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, 0.0D, -6.75D);
        poses.scale(1.0F, 1.0F, (float) (1.0D - a.lever.z * 0.25D));
        poses.translate(0.0D, 0.0D, 6.75D);
        renderModel(LOCK_SPRING, poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(a.mag.x, a.mag.y, a.mag.z);
        poses.translate(0.0D, -1.0D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) a.magRot.z));
        poses.translate(0.0D, 1.0D, 0.0D);
        renderModel(MAGAZINE, poses, buffers, light, overlay);
        renderProceduralBelt(stack, a, poses, buffers, light, overlay);
        poses.popPose();
    }

    /** Seven shells doing procedural conga. */
    private void renderProceduralBelt(ItemStack stack, Animation a, PoseStack poses,
                                      MultiBufferSource buffers, int light, int overlay) {
        double p = 0.0625D;
        double x = p * 17.0D;
        double y = p * -26.0D;
        double angle = 0.0D;
        double vx = 0.0D;
        double vy = 0.4375D;
        double reloadProgress = !a.reloading ? 1.0D : a.belt.x;
        double cycleProgress = !a.doesCycle ? 1.0D : a.cycle.x;

        double[][] shells = new double[ANGLES_LOADED.length][3];
        for (int i = 0; i < ANGLES_LOADED.length; i++) {
            shells[i][0] = x;
            shells[i][1] = y;
            shells[i][2] = angle - 90.0D;
            double delta = interp(ANGLES_UNLOADED[i], ANGLES_LOADED[i], reloadProgress);
            angle += delta;
            // Rotate the next drunk cartridge around the previous drunk cartridge.
            double rad = Math.toRadians(-delta);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double nvx = vx * cos + vy * sin;
            double nvy = vy * cos - vx * sin;
            vx = nvx;
            vy = nvy;
            x += vx;
            y += vy;
        }

        int shellAmount = a.useShellCount ? (int) a.shells.x : SexyItem.rounds(stack);
        for (int i = 0; i < shells.length - 1; i++) {
            double[] prev = shells[i];
            double[] next = shells[i + 1];
            boolean shell = shells.length - i < shellAmount + 2;
            renderShell(poses, buffers, light, overlay,
                    interp(prev[0], next[0], cycleProgress),
                    interp(prev[1], next[1], cycleProgress),
                    interp(prev[2], next[2], cycleProgress), shell);
        }
    }

    private void renderStatic(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        renderModel(GUN, poses, buffers, light, overlay);
        renderModel(BARREL, poses, buffers, light, overlay);
        renderModel(RECOIL_SPRING, poses, buffers, light, overlay);
        renderModel(HOOD, poses, buffers, light, overlay);
        renderModel(LEVER, poses, buffers, light, overlay);
        renderModel(LOCK_SPRING, poses, buffers, light, overlay);
        renderModel(MAGAZINE, poses, buffers, light, overlay);

        // Shelf-stable cartridge belt.
        double p = 0.0625D;
        renderShell(poses, buffers, light, overlay, p * 0.0D, p * -6.0D, 90.0D, true);
        renderShell(poses, buffers, light, overlay, p * 5.0D, p * 1.0D, 30.0D, true);
        renderShell(poses, buffers, light, overlay, p * 12.0D, p * -1.0D, -30.0D, true);
        renderShell(poses, buffers, light, overlay, p * 17.0D, p * -6.0D, -60.0D, true);
        renderShell(poses, buffers, light, overlay, p * 17.0D, p * -13.0D, -90.0D, true);
        renderShell(poses, buffers, light, overlay, p * 17.0D, p * -20.0D, -90.0D, true);
    }

    private void renderShell(PoseStack poses, MultiBufferSource buffers, int light, int overlay,
                             double x, double y, double rot, boolean shell) {
        poses.pushPose();
        poses.translate(x, 0.375D + y, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees((float) rot));
        poses.translate(0.0D, -0.375D, 0.0D);
        renderModel(BELT, poses, buffers, light, overlay);
        if (shell) renderModel(SHELL, poses, buffers, light, overlay);
        poses.popPose();
    }

    private static void setupContext(ItemDisplayContext context, PoseStack poses) {
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case GUI -> {
                // Unflip Mojang's GUI contribution.
                poses.scale(1.0F, -1.0F, 1.0F);
                poses.scale(1.0F, 1.0F, -1.0F);
                poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
                poses.scale(1.375F / 16.0F, 1.375F / 16.0F, 1.375F / 16.0F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
                poses.translate(0.0D, 0.5D, 0.25D);
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
                // Hip to sights, now with fewer matrix sermons.
                poses.translate(lerp(side * 0.8D, side * 0.5D, aim), lerp(-0.6D, -0.5D, aim),
                        lerp(2.4D, 2.0D, aim));
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
        // Old third-person hand bridge.
        poses.scale(0.125F, 0.125F, 0.125F);
        poses.mulPose(Axis.ZP.rotationDegrees(side * 15.0F));
        poses.mulPose(Axis.YP.rotationDegrees(side * 12.5F));
        poses.mulPose(Axis.XP.rotationDegrees(15.0F));
        poses.translate(side * 3.5D, 0.0D, 0.0D);
        // Sexy-specific camera nonsense.
        poses.scale(1.75F, 1.75F, 1.75F);
        poses.translate(side * 1.0D, 1.0D, 6.0D);
    }

    private static Animation animation(ItemStack stack) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        double time = (SexyItem.animationTimer(stack) + partial) * 50.0D;
        int amount = SexyItem.rounds(stack);
        return switch (SexyItem.animation(stack)) {
            case EQUIP -> Animation.of()
                    .equip(sequence(time, frame(45, 0, 0, 0), frame(0, 0, 0, 1000, Curve.SIN_DOWN)));
            case CYCLE -> Animation.of()
                    .recoil(sequence(time, frame(0, 0, 0, 50), frame(0, 0, -0.25, 50, Curve.SIN_DOWN),
                            frame(0, 0, 0, 100, Curve.SIN_FULL)))
                    .barrel(sequence(time, frame(0, 0, -1, 50, Curve.SIN_DOWN), frame(0, 0, 0, 150)))
                    .cycle(sequence(time, frame(1, 0, 0, 150)), true)
                    .hood(sequence(time, frame(0, 0, 0, 50), frame(3, 0, 0, 50, Curve.SIN_DOWN),
                            frame(0, 0, 0, 50, Curve.SIN_UP)))
                    .shells(new Vec(amount - 1, 0, 0));
            case CYCLE_DRY -> Animation.of()
                    .cycle(sequence(time, frame(0, 0, 18, 50)), true);
            case RELOAD -> Animation.of()
                    .lower(sequence(time, frame(15, 0, 0, 500, Curve.SIN_FULL), frame(15, 0, 0, 2750),
                            frame(12, 0, 0, 100, Curve.SIN_DOWN), frame(15, 0, 0, 100, Curve.SIN_FULL),
                            frame(15, 0, 0, 1050), frame(18, 0, 0, 100, Curve.SIN_DOWN),
                            frame(15, 0, 0, 100, Curve.SIN_FULL), frame(15, 0, 0, 300),
                            frame(0, 0, 0, 500, Curve.SIN_FULL)))
                    .lever(sequence(time, frame(0, 0, 1, 150), frame(0, 0, 1, 4700), frame(0, 0, 0, 150)))
                    .hood(sequence(time, frame(0, 0, 0, 250), frame(60, 0, 0, 500, Curve.SIN_FULL),
                            frame(60, 0, 0, 3250), frame(0, 0, 0, 500, Curve.SIN_UP)))
                    .belt(sequence(time, frame(1, 0, 0, 0), frame(1, 0, 0, 750),
                            frame(0, 0, 0, 500, Curve.SIN_UP), frame(0, 0, 0, 2000),
                            frame(1, 0, 0, 500, Curve.SIN_UP)))
                    .mag(sequence(time, frame(0, 0, 0, 1500), frame(0, -1, 0, 250, Curve.SIN_UP),
                            frame(2, -1, 0, 500, Curve.SIN_UP), frame(7, 1, 0, 250, Curve.SIN_UP),
                            frame(15, 2, 0, 250), frame(0, -2, 0, 0), frame(0, 0, 0, 500, Curve.SIN_UP)))
                    .magRot(sequence(time, frame(0, 0, 0, 2250), frame(0, 0, -180, 500, Curve.SIN_FULL),
                            frame(0, 0, 0, 0)))
                    .markReloading();
            case INSPECT -> Animation.of()
                    .bottle(sequence(time, frame(8, -8, -2, 0), frame(6, -4, -2, 500, Curve.SIN_DOWN),
                            frame(3, -3, -5, 500, Curve.SIN_FULL), frame(3, -2, -5, 1000),
                            frame(4, -6, -2, 750, Curve.SIN_FULL), frame(6, -8, -2, 500, Curve.SIN_UP)))
                    .sippy(sequence(time, frame(25, 0, 0, 0), frame(25, 0, 0, 500),
                            frame(-90, 0, 0, 500, Curve.SIN_FULL), frame(-110, 0, 0, 1000),
                            frame(25, 0, 0, 750, Curve.SIN_FULL)));
            default -> Animation.of();
        };
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

    private static double interp(double start, double end, double progress) {
        return start + (end - start) * progress;
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

    private static void renderMuzzleFlash(PoseStack poses, MultiBufferSource buffers, float progress,
                                          float shotRandom) {
        poses.pushPose();
        poses.translate(0.0D, 0.0D, 8.0D);
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        poses.mulPose(Axis.XP.rotationDegrees(shotRandom * 90.0F));
        SednaMuzzleFlash.render(poses, buffers, progress, 7.5F);
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

    /** Animation buses and the vitally important girl-dinner flag. */
    private record Animation(Vec equip, Vec lower, Vec recoil, Vec cycle, Vec barrel, Vec hood, Vec lever,
                             Vec belt, Vec mag, Vec magRot, Vec shells, Vec bottle, Vec sippy,
                             boolean doesCycle, boolean reloading, boolean useShellCount, boolean girldinner) {
        static Animation of() {
            return new Animation(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO,
                    false, false, false, false);
        }
        Animation equip(Vec v) {
            return new Animation(v, lower, recoil, cycle, barrel, hood, lever, belt, mag, magRot, shells,
                    bottle, sippy, doesCycle, reloading, useShellCount, girldinner);
        }
        Animation lower(Vec v) {
            return new Animation(equip, v, recoil, cycle, barrel, hood, lever, belt, mag, magRot, shells,
                    bottle, sippy, doesCycle, reloading, useShellCount, girldinner);
        }
        Animation recoil(Vec v) {
            return new Animation(equip, lower, v, cycle, barrel, hood, lever, belt, mag, magRot, shells,
                    bottle, sippy, doesCycle, reloading, useShellCount, girldinner);
        }
        Animation cycle(Vec v, boolean present) {
            return new Animation(equip, lower, recoil, v, barrel, hood, lever, belt, mag, magRot, shells,
                    bottle, sippy, present, reloading, useShellCount, girldinner);
        }
        Animation barrel(Vec v) {
            return new Animation(equip, lower, recoil, cycle, v, hood, lever, belt, mag, magRot, shells,
                    bottle, sippy, doesCycle, reloading, useShellCount, girldinner);
        }
        Animation hood(Vec v) {
            return new Animation(equip, lower, recoil, cycle, barrel, v, lever, belt, mag, magRot, shells,
                    bottle, sippy, doesCycle, reloading, useShellCount, girldinner);
        }
        Animation lever(Vec v) {
            return new Animation(equip, lower, recoil, cycle, barrel, hood, v, belt, mag, magRot, shells,
                    bottle, sippy, doesCycle, reloading, useShellCount, girldinner);
        }
        Animation belt(Vec v) {
            return new Animation(equip, lower, recoil, cycle, barrel, hood, lever, v, mag, magRot, shells,
                    bottle, sippy, doesCycle, reloading, useShellCount, girldinner);
        }
        Animation mag(Vec v) {
            return new Animation(equip, lower, recoil, cycle, barrel, hood, lever, belt, v, magRot, shells,
                    bottle, sippy, doesCycle, reloading, useShellCount, girldinner);
        }
        Animation magRot(Vec v) {
            return new Animation(equip, lower, recoil, cycle, barrel, hood, lever, belt, mag, v, shells,
                    bottle, sippy, doesCycle, reloading, useShellCount, girldinner);
        }
        Animation shells(Vec v) {
            return new Animation(equip, lower, recoil, cycle, barrel, hood, lever, belt, mag, magRot, v,
                    bottle, sippy, doesCycle, reloading, true, girldinner);
        }
        Animation bottle(Vec v) {
            return new Animation(equip, lower, recoil, cycle, barrel, hood, lever, belt, mag, magRot, shells,
                    v, sippy, doesCycle, reloading, useShellCount, true);
        }
        Animation sippy(Vec v) {
            return new Animation(equip, lower, recoil, cycle, barrel, hood, lever, belt, mag, magRot, shells,
                    bottle, v, doesCycle, reloading, useShellCount, girldinner);
        }
        Animation markReloading() {
            return new Animation(equip, lower, recoil, cycle, barrel, hood, lever, belt, mag, magRot, shells,
                    bottle, sippy, doesCycle, true, useShellCount, girldinner);
        }
    }
}
