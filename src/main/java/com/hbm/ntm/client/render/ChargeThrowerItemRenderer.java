package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.item.ChargeThrowerItem;
import com.hbm.ntm.weapon.ChargeThrowerAmmoType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public final class ChargeThrowerItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MODEL = id("models/weapons/charge_thrower.obj");
    private static final ResourceLocation GUN = id("textures/models/weapons/charge_thrower.png");
    private static final ResourceLocation HOOK = id("textures/models/weapons/charge_thrower_hook.png");
    private static final ResourceLocation MORTAR = id("textures/models/weapons/charge_thrower_mortar.png");
    private static final Set<String> GROUPS = Set.of("Gun", "Scope", "Hook", "Mortar", "Oomph", "Rocket");
    private EnvsuitMesh mesh;

    public ChargeThrowerItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        boolean first = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean scoped = ChargeThrowerItem.isScoped(stack);
        setupContext(context, poses, scoped);
        if (first) renderFirstPerson(stack, poses, buffers, light, overlay, scoped);
        else renderStatic(stack, poses, buffers, light, overlay, scoped);
        poses.popPose();
    }

    private void renderFirstPerson(ItemStack stack, PoseStack poses, MultiBufferSource buffers,
                                   int light, int overlay, boolean scoped) {
        boolean usingScope = scoped && ClientWeaponEvents.fullyAimed();
        if (usingScope) {
            poses.scale(3.5F, 3.5F, 3.5F);
            poses.translate(-0.5D, -1.5D, -4.0D);
        } else {
            poses.scale(0.5F, 0.5F, 0.5F);
        }
        float time = (ChargeThrowerItem.animationTimer(stack)
                + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) * 50.0F;
        ChargeThrowerItem.GunAnimation animation = ChargeThrowerItem.animation(stack);

        pivotX(poses, 0.0D, 0.0D, -7.0D, -equip(animation, time));
        pivotX(poses, 0.0D, -7.0D, 4.0D, raise(animation, time));
        poses.translate(0.0D, 0.0D, recoil(animation, time));
        pivotY(poses, 0.0D, 0.0D, -2.0D, turn(animation, time));
        pivotZ(poses, 0.0D, -1.0D, 0.0D, roll(animation, time));

        render("Gun", GUN, poses, buffers, light, overlay);
        if (scoped && !usingScope) render("Scope", GUN, poses, buffers, light, overlay);
        if (ChargeThrowerItem.rounds(stack) > 0 || animation == ChargeThrowerItem.GunAnimation.RELOAD) {
            Vec ammo = ammo(animation, time);
            poses.translate(ammo.x, ammo.y, ammo.z);
            poses.mulPose(Axis.ZP.rotationDegrees(twist(animation, time)));
            renderAmmo(stack, poses, buffers, light, overlay);
        }
    }

    private void renderStatic(ItemStack stack, PoseStack poses, MultiBufferSource buffers,
                              int light, int overlay, boolean scoped) {
        render("Gun", GUN, poses, buffers, light, overlay);
        if (scoped) render("Scope", GUN, poses, buffers, light, overlay);
        if (ChargeThrowerItem.rounds(stack) > 0) renderAmmo(stack, poses, buffers, light, overlay);
    }

    private void renderAmmo(ItemStack stack, PoseStack poses, MultiBufferSource buffers,
                            int light, int overlay) {
        ChargeThrowerAmmoType ammo = ChargeThrowerItem.loadedAmmo(stack);
        if (ammo.kind() == ChargeThrowerAmmoType.Kind.HOOK) {
            render("Hook", HOOK, poses, buffers, light, overlay);
        } else {
            render("Mortar", MORTAR, poses, buffers, light, overlay);
            if (ammo.kind() == ChargeThrowerAmmoType.Kind.CHARGED_MORTAR) {
                render("Oomph", MORTAR, poses, buffers, light, overlay);
            }
        }
    }

    private void render(String group, ResourceLocation texture, PoseStack poses,
                        MultiBufferSource buffers, int light, int overlay) {
        mesh().render(group, poses.last(), buffers.getBuffer(RenderType.entityCutout(texture)),
                1.0F, light, overlay, -1);
    }

    private static void setupContext(ItemDisplayContext context, PoseStack poses, boolean scoped) {
        switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                double side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 1.0D : -1.0D;
                float aim = ClientWeaponEvents.aimingProgress(
                        Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
                poses.mulPose(Axis.YP.rotationDegrees(180.0F));
                poses.translate(0.0D, 0.0D, 0.875D);
                poses.translate(lerp(side * 1.2D, scoped ? -0.15625D : side * 0.75D, aim),
                        lerp(-1.0D, scoped ? -0.8125D : -0.625D, aim),
                        lerp(2.8D, scoped ? 1.6875D : 1.75D, aim));
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                setupThirdPerson(context, poses);
                poses.scale(1.5F, 1.5F, 1.5F);
                poses.translate(0.75D, 1.0D, 4.0D);
            }
            case GUI -> {
                poses.scale(1.0F, -1.0F, -1.0F);
                poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
                poses.scale(1.25F / 16.0F, 1.25F / 16.0F, 1.25F / 16.0F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
                poses.translate(0.0D, 0.0D, -0.625D);
            }
            case GROUND, FIXED -> {
                poses.scale(0.125F, 0.125F, 0.125F);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
            default -> poses.scale(0.125F, 0.125F, 0.125F);
        }
    }

    static void setupThirdPerson(ItemDisplayContext context, PoseStack poses) {
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

    private static float equip(ChargeThrowerItem.GunAnimation animation, float time) {
        return animation == ChargeThrowerItem.GunAnimation.EQUIP ? lerp(-45.0F, 0.0F, sineDown(time / 500.0F)) : 0.0F;
    }
    private static float recoil(ChargeThrowerItem.GunAnimation animation, float time) {
        if (animation != ChargeThrowerItem.GunAnimation.CYCLE) return 0.0F;
        if (time < 100.0F) return lerp(0.0F, -1.0F, sineDown(time / 100.0F));
        return lerp(-1.0F, 0.0F, smooth((time - 100.0F) / 250.0F));
    }
    private static float raise(ChargeThrowerItem.GunAnimation animation, float time) {
        if (animation != ChargeThrowerItem.GunAnimation.RELOAD) return 0.0F;
        if (time < 500.0F) return lerp(0.0F, -45.0F, smooth(time / 500.0F));
        if (time < 2500.0F) return -45.0F;
        return lerp(-45.0F, 0.0F, smooth((time - 2500.0F) / 500.0F));
    }
    private static Vec ammo(ChargeThrowerItem.GunAnimation animation, float time) {
        if (animation != ChargeThrowerItem.GunAnimation.RELOAD) return Vec.ZERO;
        if (time < 500.0F) return new Vec(0.0D, -10.0D, -5.0D);
        if (time < 1250.0F) return new Vec(0.0D, lerp(-10.0D, 0.0D, smooth((time - 500.0F) / 750.0F)),
                lerp(-5.0D, 5.0D, smooth((time - 500.0F) / 750.0F)));
        if (time < 1750.0F) return new Vec(0.0D, 0.0D, lerp(5.0D, 0.0D, sineUp((time - 1250.0F) / 500.0F)));
        return Vec.ZERO;
    }
    private static float twist(ChargeThrowerItem.GunAnimation animation, float time) {
        if (animation != ChargeThrowerItem.GunAnimation.RELOAD) return 0.0F;
        if (time < 2000.0F) return 25.0F;
        return lerp(25.0F, 0.0F, Mth.clamp((time - 2000.0F) / 150.0F, 0.0F, 1.0F));
    }
    private static float turn(ChargeThrowerItem.GunAnimation animation, float time) {
        if (animation != ChargeThrowerItem.GunAnimation.INSPECT) return 0.0F;
        if (time < 500.0F) return lerp(0.0F, 60.0F, smooth(time / 500.0F));
        if (time < 2250.0F) return 60.0F;
        return lerp(60.0F, 0.0F, smooth((time - 2250.0F) / 500.0F));
    }
    private static float roll(ChargeThrowerItem.GunAnimation animation, float time) {
        if (animation != ChargeThrowerItem.GunAnimation.INSPECT || time < 750.0F) return 0.0F;
        if (time < 1250.0F) return lerp(0.0F, -90.0F, smooth((time - 750.0F) / 500.0F));
        if (time < 2250.0F) return -90.0F;
        return lerp(-90.0F, 0.0F, smooth((time - 2250.0F) / 500.0F));
    }

    private static void pivotX(PoseStack poses, double x, double y, double z, float angle) {
        poses.translate(x, y, z); poses.mulPose(Axis.XP.rotationDegrees(angle)); poses.translate(-x, -y, -z);
    }
    private static void pivotY(PoseStack poses, double x, double y, double z, float angle) {
        poses.translate(x, y, z); poses.mulPose(Axis.YP.rotationDegrees(angle)); poses.translate(-x, -y, -z);
    }
    private static void pivotZ(PoseStack poses, double x, double y, double z, float angle) {
        poses.translate(x, y, z); poses.mulPose(Axis.ZP.rotationDegrees(angle)); poses.translate(-x, -y, -z);
    }
    private EnvsuitMesh mesh() {
        if (mesh == null) mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(), MODEL, GROUPS, "Charge Thrower");
        return mesh;
    }
    private static float sineDown(float value) { return (float) Math.sin(Mth.clamp(value, 0.0F, 1.0F) * Math.PI * 0.5D); }
    private static float sineUp(float value) { return 1.0F - (float) Math.cos(Mth.clamp(value, 0.0F, 1.0F) * Math.PI * 0.5D); }
    private static float smooth(float value) { return (1.0F - (float) Math.cos(Mth.clamp(value, 0.0F, 1.0F) * Math.PI)) * 0.5F; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * Mth.clamp(t, 0.0F, 1.0F); }
    private static double lerp(double a, double b, float t) { return a + (b - a) * Mth.clamp(t, 0.0F, 1.0F); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path); }
    private record Vec(double x, double y, double z) { private static final Vec ZERO = new Vec(0.0D, 0.0D, 0.0D); }
}
