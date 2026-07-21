package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.item.FollyItem;
import com.hbm.ntm.registry.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class FollyItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MODEL = id("models/weapons/folly.obj");
    private static final ResourceLocation TEXTURE = id("textures/models/weapons/moonlight.png");
    private static final Set<String> GROUPS = Set.of("Cannon", "Barrel", "Shell", "Breech", "Cog");
    private EnvsuitMesh mesh;
    private boolean jingle;

    public FollyItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof FollyItem)) return;
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
        float time = (FollyItem.animationTimer(stack) + partial) * 50.0F;
        FollyItem.GunAnimation animation = FollyItem.animation(stack);
        Animation state = animation(animation, time);

        poses.scale(0.75F, 0.75F, 0.75F);
        pivotX(poses, 0.0D, 1.0D, -4.0D, -state.equip);
        pivotX(poses, 0.0D, -2.0D, -2.0D, state.load);
        render("Cannon", poses, buffers, light, overlay);

        poses.pushPose();
        poses.translate(0.0D, 0.0D, state.recoilZ);
        render("Barrel", poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, state.shellY, state.shellZ);
        render("Shell", poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.0D, state.breechY, state.breechZ);
        render("Breech", poses, buffers, light, overlay);
        poses.translate(0.0D, 1.0D, 0.0D);
        poses.mulPose(Axis.ZP.rotationDegrees(state.screw));
        poses.translate(0.0D, -1.0D, 0.0D);
        render("Cog", poses, buffers, light, overlay);
        poses.popPose();

        boolean terminal = ClientWeaponEvents.fullyAimed() && FollyItem.aiming(stack)
                && animation == FollyItem.GunAnimation.SPINUP;
        if (terminal) {
            if (!jingle && time >= 3_000.0F && time <= 5_000.0F) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.level().playLocalSound(minecraft.player.getX(), minecraft.player.getY(),
                            minecraft.player.getZ(), ModSounds.GUN_FOLLY_VSTAR.get(), SoundSource.PLAYERS,
                            0.5F, 1.0F, false);
                }
                jingle = true;
            }
            renderTerminal(stack, time, poses, buffers);
        } else jingle = false;
    }

    private void renderStatic(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        for (String group : GROUPS) render(group, poses, buffers, light, overlay);
    }

    private static void renderTerminal(ItemStack stack, float elapsed, PoseStack poses,
                                       MultiBufferSource buffers) {
        Font font = Minecraft.getInstance().font;
        int orange = 0xFFFF7F00;
        String splash = bootSplash(elapsed);
        if (elapsed > 5_000.0F) {
            String message = FollyItem.rounds(stack) > 0 ? "+" : "No ammo";
            drawCentered(font, message, 0.01F, 2.0F, 1.0F, poses, buffers, orange);
        }
        if (!splash.isEmpty()) drawCentered(font, splash, 0.02F, 2.0F, 1.0F, poses, buffers, orange);

        List<String> tty = tty(elapsed);
        if (tty.isEmpty()) return;
        poses.pushPose();
        poses.translate(2.5D, 1.375D, -2.75D);
        poses.scale(0.005F, -0.005F, 0.005F);
        poses.mulPose(Axis.YP.rotationDegrees(180.0F));
        for (int i = 0; i < tty.size(); i++) {
            font.drawInBatch(tty.get(i), 0.0F, i * (font.lineHeight + 2.0F), orange, false,
                    poses.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET,
                    0, LightTexture.FULL_BRIGHT);
        }
        poses.popPose();
    }

    private static void drawCentered(Font font, String text, float scale, float x, float y,
                                     PoseStack poses, MultiBufferSource buffers, int color) {
        poses.pushPose();
        poses.translate(x + font.width(text) * scale * 0.5D,
                y + font.lineHeight * scale * 0.5D, -2.75D);
        poses.scale(scale, -scale, scale);
        poses.mulPose(Axis.YP.rotationDegrees(180.0F));
        font.drawInBatch(text, 0.0F, 0.0F, color, false, poses.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poses.popPose();
    }

    private static String bootSplash(float elapsed) {
        if (elapsed < 3_000.0F || elapsed > 5_000.0F) return "";
        int index = (int) ((elapsed - 3_000.0F) * 35.0F / 2_000.0F) - 10;
        char[] letters = "VStarOS".toCharArray();
        StringBuilder splash = new StringBuilder();
        for (int i = 0; i < letters.length; i++) {
            if (i < index - 1) splash.append(ChatFormatting.LIGHT_PURPLE);
            else if (i == index - 1 || i == index + 1) splash.append(ChatFormatting.AQUA);
            else if (i == index) splash.append(ChatFormatting.WHITE);
            else if (i == index + 2) splash.append(ChatFormatting.LIGHT_PURPLE);
            else if (i > index + 2) splash.append(ChatFormatting.BLACK);
            splash.append(letters[i]);
        }
        return splash.toString();
    }

    private static List<String> tty(float elapsed) {
        List<String> lines = new ArrayList<>();
        if (elapsed < 3_000.0F) {
            if (elapsed > 250.0F) lines.add("POST successful - Code 0");
            if (elapsed > 500.0F) lines.add("8,388,608 bytes of RAM installed");
            if (elapsed > 500.0F) lines.add("5,187,427 bytes available");
            if (elapsed > 750.0F) lines.add("Reticulating splines...");
            if (elapsed > 1_500.0F) lines.add("No keyboard found!");
            if (elapsed > 2_000.0F) lines.add("Booting from /dev/sda1...");
        }
        if (elapsed > 5_000.0F) {
            Minecraft minecraft = Minecraft.getInstance();
            String target = "N/A";
            HitResult hit = minecraft.hitResult;
            if (hit instanceof EntityHitResult entityHit) target = entityHit.getEntity().getName().getString();
            else if (minecraft.player != null) {
                HitResult longHit = minecraft.player.pick(250.0D, 0.0F, false);
                if (longHit instanceof BlockHitResult block && block.getType() != HitResult.Type.MISS) {
                    target = block.getBlockPos().getX() + "/" + block.getBlockPos().getY()
                            + "/" + block.getBlockPos().getZ();
                }
            }
            lines.add("Target: " + target);
            if (minecraft.player != null) {
                double angle = (int) (-minecraft.player.getXRot() * 100.0F) / 100.0D;
                lines.add("Angle: " + angle);
            }
        }
        return lines;
    }

    private void render(String group, PoseStack poses, MultiBufferSource buffers,
                        int light, int overlay) {
        mesh().render(group, poses.last(), buffers.getBuffer(RenderType.entityCutout(TEXTURE)),
                1.0F, light, overlay, -1);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) {
            mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(), MODEL, GROUPS, "Folly");
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
                poses.scale(1.25F / 16.0F, 1.25F / 16.0F, 1.25F / 16.0F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
                poses.translate(0.0D, -0.5D, 0.0D);
            }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                float aim = ClientWeaponEvents.aimingProgress(partial);
                double side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 1.0D : -1.0D;
                poses.mulPose(Axis.YP.rotationDegrees(180.0F));
                poses.translate(0.0D, 0.0D, 0.875D);
                poses.translate(lerp(side * 2.0D, side * 1.5D, aim),
                        lerp(-1.2D, -0.75D, aim), lerp(2.2D, 1.8D, aim));
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                setupThirdPerson(context, poses);
                poses.scale(3.0F, 3.0F, 3.0F);
                poses.translate(-0.25D, 0.5D, 3.0D);
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

    private static Animation animation(FollyItem.GunAnimation animation, float time) {
        float equip = 0.0F;
        float recoil = 0.0F;
        float load = 0.0F;
        float shellY = 0.0F;
        float shellZ = 0.0F;
        float breechY = 0.0F;
        float breechZ = 0.0F;
        float screw = 0.0F;
        if (animation == FollyItem.GunAnimation.EQUIP) {
            equip = time < 1_500.0F ? lerp(-60.0F, 5.0F, sineDown(time / 1_500.0F))
                    : time < 2_000.0F ? lerp(5.0F, 0.0F, smooth((time - 1_500.0F) / 500.0F)) : 0.0F;
        }
        if (animation == FollyItem.GunAnimation.CYCLE) {
            recoil = time < 50.0F ? lerp(0.0F, -4.5F, time / 50.0F)
                    : time < 550.0F ? -4.5F
                    : time < 1_050.0F ? lerp(-4.5F, 0.0F, sineUp((time - 550.0F) / 500.0F)) : 0.0F;
            load = time < 50.0F ? 0.0F
                    : time < 300.0F ? lerp(0.0F, -25.0F, sineDown((time - 50.0F) / 250.0F))
                    : time < 1_300.0F ? lerp(-25.0F, 0.0F, smooth((time - 300.0F) / 1_000.0F)) : 0.0F;
        }
        if (animation == FollyItem.GunAnimation.RELOAD) {
            load = time < 1_000.0F ? lerp(0.0F, 60.0F, smooth(time / 1_000.0F))
                    : time < 7_000.0F ? 60.0F
                    : time < 8_000.0F ? lerp(60.0F, 0.0F, smooth((time - 7_000.0F) / 1_000.0F)) : 0.0F;
            screw = time < 1_000.0F ? 0.0F
                    : time < 2_000.0F ? lerp(0.0F, -135.0F, smooth((time - 1_000.0F) / 1_000.0F))
                    : time < 6_000.0F ? -135.0F
                    : time < 7_000.0F ? lerp(-135.0F, 0.0F, smooth((time - 6_000.0F) / 1_000.0F)) : 0.0F;
            breechZ = time < 1_000.0F ? 0.0F
                    : time < 2_000.0F ? lerp(0.0F, -0.5F, smooth((time - 1_000.0F) / 1_000.0F))
                    : time < 6_000.0F ? -0.5F
                    : time < 7_000.0F ? lerp(-0.5F, 0.0F, smooth((time - 6_000.0F) / 1_000.0F)) : 0.0F;
            breechY = time < 2_000.0F ? 0.0F
                    : time < 3_000.0F ? lerp(0.0F, -4.0F, smooth((time - 2_000.0F) / 1_000.0F))
                    : time < 5_000.0F ? -4.0F
                    : time < 6_000.0F ? lerp(-4.0F, 0.0F, smooth((time - 5_000.0F) / 1_000.0F)) : 0.0F;
            shellY = time < 3_000.0F ? -4.0F
                    : time < 4_000.0F ? lerp(-4.0F, 0.0F, smooth((time - 3_000.0F) / 1_000.0F)) : 0.0F;
            shellZ = time < 4_000.0F ? -4.5F
                    : time < 4_500.0F ? lerp(-4.5F, 0.0F, sineUp((time - 4_000.0F) / 500.0F)) : 0.0F;
        }
        return new Animation(equip, recoil, load, shellY, shellZ, breechY, breechZ, screw);
    }

    private static void pivotX(PoseStack poses, double x, double y, double z, float angle) {
        if (angle == 0.0F) return;
        poses.translate(x, y, z);
        poses.mulPose(Axis.XP.rotationDegrees(angle));
        poses.translate(-x, -y, -z);
    }

    private static float sineDown(float value) {
        return (float) Math.sin(Math.PI * 0.5D * Mth.clamp(value, 0.0F, 1.0F));
    }

    private static float sineUp(float value) {
        return 1.0F - (float) Math.cos(Math.PI * 0.5D * Mth.clamp(value, 0.0F, 1.0F));
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return (1.0F - (float) Math.cos(Math.PI * clamped)) * 0.5F;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * Mth.clamp(amount, 0.0F, 1.0F);
    }

    private static double lerp(double from, double to, float amount) {
        return from + (to - from) * Mth.clamp(amount, 0.0F, 1.0F);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }

    private record Animation(float equip, float recoilZ, float load, float shellY,
                             float shellZ, float breechY, float breechZ, float screw) { }
}
