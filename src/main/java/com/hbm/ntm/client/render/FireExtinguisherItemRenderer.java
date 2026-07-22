package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.item.FireExtinguisherItem;
import com.hbm.ntm.weapon.FireExtinguisherAmmoType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;

/** Original Fire Extinguisher mesh, loaded-tank skins, and authored poses. */
public final class FireExtinguisherItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MODEL = id("models/weapons/fireext.obj");
    private static final ResourceLocation WATER = id("textures/models/weapons/fireext_normal.png");
    private static final ResourceLocation FOAM = id("textures/models/weapons/fireext_foam.png");
    private static final ResourceLocation SAND = id("textures/models/weapons/fireext_sand.png");
    private static final Set<String> GROUPS = Set.of("Cylinder");
    private EnvsuitMesh mesh;

    public FireExtinguisherItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                float side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -1.0F : 1.0F;
                setupForgeEquipped(side, poses);
                poses.mulPose(Axis.ZP.rotationDegrees(side * 25.0F));
                poses.translate(side * 0.5D, -0.5D, -0.5D);
                poses.mulPose(Axis.YP.rotationDegrees(side * 80.0F));
                poses.scale(0.35F, 0.35F, 0.35F);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                float side = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -1.0F : 1.0F;
                setupLegacyEquipped(context, poses);
                poses.scale(0.5F, 0.5F, 0.5F);
                poses.mulPose(Axis.ZP.rotationDegrees(side * 20.0F));
                poses.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(side * -5.0D),
                        new Vector3f(0.0F, 1.0F, 1.0F).normalize()));
                poses.mulPose(Axis.YP.rotationDegrees(side * 10.0F));
                poses.mulPose(Axis.XP.rotationDegrees(15.0F));
                poses.translate(side * 0.75D, -2.75D, 0.5D);
            }
            case GUI -> {
                poses.scale(1.0F, -1.0F, 1.0F);
                poses.translate(-6.0D / 16.0D, 6.0D / 16.0D, 0.0D);
                poses.mulPose(Axis.YN.rotationDegrees(90.0F));
                poses.mulPose(Axis.XN.rotationDegrees(135.0F));
                poses.mulPose(Axis.YP.rotationDegrees((Util.getMillis() / 10L) % 360L));
                poses.scale(4.5F / 16.0F, 4.5F / 16.0F, -4.5F / 16.0F);
            }
            case GROUND, FIXED -> poses.scale(0.3F, 0.3F, 0.3F);
            default -> poses.scale(0.3F, 0.3F, 0.3F);
        }
        mesh().render("Cylinder", poses.last(),
                buffers.getBuffer(RenderType.entityCutout(texture(stack))), 1.0F, light, overlay, -1);
        poses.popPose();
    }

    private static void setupLegacyEquipped(ItemDisplayContext context, PoseStack poses) {
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
        setupForgeEquipped(side, poses);
    }

    private static void setupForgeEquipped(float side, PoseStack poses) {
        poses.translate(0.0D, -0.3D, 0.0D);
        poses.scale(1.5F, 1.5F, 1.5F);
        poses.mulPose(Axis.YP.rotationDegrees(side * 50.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(side * 335.0F));
        poses.translate(-side * 0.9375D, -0.0625D, 0.0D);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(),
                MODEL, GROUPS, "Fire Extinguisher");
        return mesh;
    }

    private static ResourceLocation texture(ItemStack stack) {
        return switch (FireExtinguisherItem.loadedType(stack)) {
            case WATER -> WATER;
            case FOAM -> FOAM;
            case SAND -> SAND;
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }
}
