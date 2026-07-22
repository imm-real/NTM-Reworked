package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.item.DrillItem;
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

public final class DrillItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MODEL = id("models/weapons/drill.obj");
    private static final ResourceLocation TEXTURE = id("textures/models/weapons/drill.png");
    private static final Set<String> GROUPS = Set.of(
            "Base", "Gauge", "Piston1", "Piston2", "Piston3", "DrillBack", "DrillFront");
    private EnvsuitMesh mesh;

    public DrillItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        boolean first = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        setupContext(context, poses);
        if (first) renderFirstPerson(stack, poses, buffers, light, overlay);
        else renderAll(poses, buffers, light, overlay);
        poses.popPose();
    }

    private void renderFirstPerson(ItemStack stack, PoseStack poses, MultiBufferSource buffers,
                                   int light, int overlay) {
        poses.scale(0.375F, 0.375F, 0.375F);
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float time = (DrillItem.animationTimer(stack) + partial) * 50.0F;
        DrillItem.GunAnimation animation = DrillItem.animation(stack);
        float deploy = deploy(animation, time);
        float spin = spin(animation, time);

        poses.mulPose(Axis.YP.rotationDegrees(15.0F * (1.0F - deploy * 0.5F)));
        poses.mulPose(Axis.XP.rotationDegrees(-10.0F * (1.0F - deploy * 0.5F)));
        poses.translate(0.0D, 2.0D, -6.0D);
        float equip = animation == DrillItem.GunAnimation.EQUIP
                ? 1.0F - sineDown(time / 750.0F) : 0.0F;
        poses.mulPose(Axis.YP.rotationDegrees(equip * -45.0F));
        poses.mulPose(Axis.XP.rotationDegrees(equip * -20.0F));
        poses.translate(0.0D, -2.0D, 6.0D);
        if (animation == DrillItem.GunAnimation.INSPECT) {
            poses.mulPose(Axis.XP.rotationDegrees(inspectLift(time)));
        }
        poses.translate(0.0D, 0.0D, deploy);

        render("Base", poses, buffers, light, overlay);
        poses.pushPose();
        poses.translate(1.0D, 2.0625D, -1.75D);
        poses.mulPose(Axis.XP.rotationDegrees(45.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(-135.0F + (float) DrillItem.fuel(stack) / DrillItem.CAPACITY * 270.0F));
        poses.mulPose(Axis.XN.rotationDegrees(45.0F));
        poses.translate(-1.0D, -2.0625D, 1.75D);
        render("Gauge", poses, buffers, light, overlay);
        poses.popPose();

        renderPiston("Piston1", spin * 5.0F, 0.0D, poses, buffers, light, overlay);
        renderPiston("Piston2", spin * 5.0F, Math.PI * 2.0D / 3.0D, poses, buffers, light, overlay);
        renderPiston("Piston3", spin * 5.0F, Math.PI * 4.0D / 3.0D, poses, buffers, light, overlay);
        poses.pushPose();
        poses.mulPose(Axis.ZN.rotationDegrees(spin));
        render("DrillBack", poses, buffers, light, overlay);
        poses.popPose();
        poses.pushPose();
        poses.mulPose(Axis.ZP.rotationDegrees(spin));
        render("DrillFront", poses, buffers, light, overlay);
        poses.popPose();
    }

    private void renderPiston(String group, float rotation, double phase, PoseStack poses,
                              MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.0D, Math.sin(Math.toRadians(rotation) + phase) * 0.125D - 0.125D, 0.0D);
        render(group, poses, buffers, light, overlay);
        poses.popPose();
    }

    private void renderAll(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        for (String group : GROUPS) render(group, poses, buffers, light, overlay);
    }

    private void render(String group, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        mesh().render(group, poses.last(), buffers.getBuffer(RenderType.entityCutout(TEXTURE)),
                1.0F, light, overlay, -1);
    }

    private static void setupContext(ItemDisplayContext context, PoseStack poses) {
        switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                double side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 1.0D : -1.0D;
                float aim = ClientWeaponEvents.aimingProgress(
                        Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
                poses.mulPose(Axis.YP.rotationDegrees(180.0F));
                poses.translate(0.0D, 0.0D, 0.875D);
                poses.translate(lerp(side * 1.0D, side * 0.8D, aim), -1.4D, lerp(1.4D, 1.0D, aim));
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                ChargeThrowerItemRenderer.setupThirdPerson(context, poses);
                poses.scale(2.25F, 2.25F, 2.25F);
                double side = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -1.0D : 1.0D;
                poses.translate(side, -2.0D, 6.0D);
            }
            case GUI -> {
                poses.scale(1.0F, -1.0F, -1.0F);
                poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
                poses.scale(1.25F / 16.0F, 1.25F / 16.0F, 1.25F / 16.0F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
                poses.translate(-0.5D, 0.0D, 0.0D);
            }
            case GROUND, FIXED -> {
                poses.scale(0.125F, 0.125F, 0.125F);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
            default -> poses.scale(0.125F, 0.125F, 0.125F);
        }
    }

    private static float deploy(DrillItem.GunAnimation animation, float time) {
        if (animation == DrillItem.GunAnimation.CYCLE) {
            if (time < 500.0F) return sineFull(time / 500.0F);
            if (time < 1500.0F) return 1.0F;
            return 1.0F - sineFull((time - 1500.0F) / 500.0F);
        }
        if (animation == DrillItem.GunAnimation.CYCLE_DRY) {
            return time < 250.0F ? sineFull(time / 250.0F) * 0.25F
                    : (1.0F - sineFull((time - 250.0F) / 250.0F)) * 0.25F;
        }
        return 0.0F;
    }

    private static float spin(DrillItem.GunAnimation animation, float time) {
        if (animation == DrillItem.GunAnimation.CYCLE) {
            if (time < 1500.0F) return time / 1500.0F * 540.0F;
            return 540.0F + sineDown((time - 1500.0F) / 1750.0F) * 540.0F;
        }
        return animation == DrillItem.GunAnimation.CYCLE_DRY ? sineDown(time / 1500.0F) * 360.0F : 0.0F;
    }

    private static float inspectLift(float time) {
        if (time < 500.0F) return -45.0F * sineFull(time / 500.0F);
        if (time < 1500.0F) return -45.0F;
        return -45.0F * (1.0F - sineDown((time - 1500.0F) / 500.0F));
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(), MODEL, GROUPS, "Powered Drill");
        return mesh;
    }

    private static float sineDown(float value) { return (float) Math.sin(Mth.clamp(value, 0.0F, 1.0F) * Math.PI * 0.5D); }
    private static float sineFull(float value) { return (1.0F - (float) Math.cos(Mth.clamp(value, 0.0F, 1.0F) * Math.PI)) * 0.5F; }
    private static double lerp(double a, double b, float t) { return a + (b - a) * Mth.clamp(t, 0.0F, 1.0F); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path); }
}
