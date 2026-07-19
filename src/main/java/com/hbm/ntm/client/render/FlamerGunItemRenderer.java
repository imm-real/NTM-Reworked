package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.ClientWeaponEvents;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.item.FlamerGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Original flamethrower mesh, animation, gauge, shield, and authored item poses. */
public final class FlamerGunItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MODEL = id("models/weapons/flamethrower.obj");
    private static final ResourceLocation ANIMATION = id("models/weapons/animations/flamethrower.json");
    private static final ResourceLocation FLAMETHROWER = id("textures/models/weapons/flamethrower.png");
    private static final ResourceLocation TOPAZ = id("textures/models/weapons/flamethrower_topaz.png");
    private static final ResourceLocation DAYBREAKER = id("textures/models/weapons/flamethrower_daybreaker.png");
    private static final Set<String> GROUPS = Set.of("Gun", "Tank", "Gauge", "HeatShield");

    private EnvsuitMesh mesh;
    private LegacyWeaponAnimation animations;

    public FlamerGunItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static void validateAnimationResources(ResourceManager resources) {
        LegacyWeaponAnimation.load(resources, ANIMATION);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof FlamerGunItem gun)) return;
        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        poses.pushPose();
        setupContext(context, poses);
        if (firstPerson) renderFirstPerson(stack, gun, poses, buffers, light, overlay);
        else renderStatic(gun, poses, buffers, light, overlay);
        poses.popPose();
    }

    private void renderFirstPerson(ItemStack stack, FlamerGunItem gun, PoseStack poses,
                                   MultiBufferSource buffers, int light, int overlay) {
        poses.scale(0.375F, 0.375F, 0.375F);
        pivotX(poses, 0.0D, 2.0D, -6.0D, -equipAngle(stack));
        pivotZ(poses, 0.0D, 1.0D, 0.0D, rotateAngle(stack));

        double time = FlamerGunItem.animationTimer(stack) * 50.0D
                + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true) * 50.0D;
        String clip = FlamerGunItem.animation(stack) == FlamerGunItem.GunAnimation.RELOAD ? "Reload" : null;

        poses.pushPose();
        apply(poses, clip, "Gun", time);
        render("Gun", texture(gun.variant()), poses, buffers, light, overlay);
        if (gun.variant() == FlamerGunItem.Variant.DAYBREAKER) {
            render("HeatShield", texture(gun.variant()), poses, buffers, light, overlay);
        }
        poses.popPose();

        poses.pushPose();
        apply(poses, clip, "Tank", time);
        render("Tank", texture(gun.variant()), poses, buffers, light, overlay);
        poses.popPose();

        poses.pushPose();
        apply(poses, clip, "Gauge", time);
        pivotZ(poses, 1.25D, 1.25D, 0.0D,
                -135.0D + FlamerGunItem.rounds(stack) * 270.0D / gun.gunCapacity());
        render("Gauge", texture(gun.variant()), poses, buffers, light, overlay);
        poses.popPose();
    }

    private void renderStatic(FlamerGunItem gun, PoseStack poses, MultiBufferSource buffers,
                              int light, int overlay) {
        ResourceLocation texture = texture(gun.variant());
        render("Gun", texture, poses, buffers, light, overlay);
        render("Tank", texture, poses, buffers, light, overlay);
        render("Gauge", texture, poses, buffers, light, overlay);
        if (gun.variant() == FlamerGunItem.Variant.DAYBREAKER) {
            render("HeatShield", texture, poses, buffers, light, overlay);
        }
    }

    private void apply(PoseStack poses, String clip, String group, double time) {
        if (clip != null) LegacyWeaponAnimation.apply(poses, animations().transform(clip, group, time));
    }

    private void render(String group, ResourceLocation texture, PoseStack poses,
                        MultiBufferSource buffers, int light, int overlay) {
        mesh().render(group, poses.last(), buffers.getBuffer(RenderType.entityCutout(texture)),
                1.0F, light, overlay, -1);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(),
                MODEL, GROUPS, "Flamethrower");
        return mesh;
    }

    private LegacyWeaponAnimation animations() {
        if (animations == null) animations = LegacyWeaponAnimation.load(
                Minecraft.getInstance().getResourceManager(), ANIMATION);
        return animations;
    }

    private static void setupContext(ItemDisplayContext context, PoseStack poses) {
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case GUI -> {
                poses.scale(1.0F, -1.0F, 1.0F);
                poses.scale(1.0F, 1.0F, -1.0F);
                poses.mulPose(Axis.ZP.rotationDegrees(225.0F));
                poses.mulPose(Axis.YP.rotationDegrees(90.0F));
                poses.scale(1.25F / 16.0F, 1.25F / 16.0F, 1.25F / 16.0F);
                poses.mulPose(Axis.XP.rotationDegrees(25.0F));
                poses.mulPose(Axis.YP.rotationDegrees(45.0F));
                poses.translate(-1.0D, 1.0D, 0.0D);
            }
            case GROUND, FIXED -> {
                poses.scale(0.125F, 0.125F, 0.125F);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> setupThirdPerson(context, poses);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                poses.mulPose(Axis.YP.rotationDegrees(180.0F));
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                float aim = ClientWeaponEvents.aimingProgress(partial);
                double side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 1.0D : -1.0D;
                poses.translate(0.0D, 0.0D, 0.875D);
                poses.translate(lerp(side * 1.2D, 0.0D, aim),
                        lerp(-1.2D, -0.578125D, aim), lerp(2.2D, 0.25D, aim));
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
        poses.scale(1.75F, 1.75F, 1.75F);
        poses.translate(0.0D, -3.0D, 4.0D);
    }

    private static double equipAngle(ItemStack stack) {
        if (FlamerGunItem.animation(stack) != FlamerGunItem.GunAnimation.EQUIP) return 0.0D;
        double progress = Mth.clamp(FlamerGunItem.animationTimer(stack) * 50.0D / 500.0D, 0.0D, 1.0D);
        return -45.0D + 45.0D * Math.sin(progress * Math.PI * 0.5D);
    }

    private static double rotateAngle(ItemStack stack) {
        FlamerGunItem.GunAnimation animation = FlamerGunItem.animation(stack);
        if (animation != FlamerGunItem.GunAnimation.INSPECT
                && animation != FlamerGunItem.GunAnimation.JAMMED) return 0.0D;
        double time = FlamerGunItem.animationTimer(stack) * 50.0D;
        if (time < 250.0D) return 45.0D * sinFull(time / 250.0D);
        if (time < 600.0D) return 45.0D;
        if (time < 750.0D) return 45.0D + (-60.0D * sinFull((time - 600.0D) / 150.0D));
        if (time < 850.0D) return -15.0D + 15.0D * sinFull((time - 750.0D) / 100.0D);
        return 0.0D;
    }

    private static double sinFull(double progress) {
        return (-Math.cos(Mth.clamp(progress, 0.0D, 1.0D) * Math.PI) + 1.0D) * 0.5D;
    }

    private static void pivotX(PoseStack poses, double x, double y, double z, double angle) {
        poses.translate(x, y, z);
        poses.mulPose(Axis.XP.rotationDegrees((float) angle));
        poses.translate(-x, -y, -z);
    }

    private static void pivotZ(PoseStack poses, double x, double y, double z, double angle) {
        poses.translate(x, y, z);
        poses.mulPose(Axis.ZP.rotationDegrees((float) angle));
        poses.translate(-x, -y, -z);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static ResourceLocation texture(FlamerGunItem.Variant variant) {
        return switch (variant) {
            case FLAMETHROWER -> FLAMETHROWER;
            case TOPAZ -> TOPAZ;
            case DAYBREAKER -> DAYBREAKER;
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }
}
