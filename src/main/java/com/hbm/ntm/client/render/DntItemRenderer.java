package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Exact ArmorDNT item transforms and OBJ groups from 1.7.10. */
public final class DntItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "models/armor/dnt.obj");
    private static final Set<String> GROUPS = Set.of(
            "Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg", "LeftBoot", "RightBoot");
    private static final ResourceLocation HELMET = texture("dnt_helmet");
    private static final ResourceLocation CHEST = texture("dnt_chest");
    private static final ResourceLocation ARM = texture("dnt_arm");
    private static final ResourceLocation LEG = texture("dnt_leg");

    private final ArmorItem.Type piece;
    private EnvsuitMesh mesh;

    public DntItemRenderer(ArmorItem.Type piece) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.piece = piece;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        switch (context) {
            case GUI -> {
                EnvsuitItemRenderer.setupInventory(poses,
                        piece == ArmorItem.Type.HELMET ? -1.0D : 0.0D);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND ->
                    EnvsuitItemRenderer.setupThirdPerson(context, poses);
            case GROUND -> EnvsuitItemRenderer.setupNonInventory(poses, true, true, 1.0F);
            case FIRST_PERSON_LEFT_HAND -> EnvsuitItemRenderer.setupNonInventory(poses, false, true, -1.0F);
            case FIRST_PERSON_RIGHT_HAND, FIXED, HEAD, NONE ->
                    EnvsuitItemRenderer.setupNonInventory(poses, false, true, 1.0F);
        }
        renderPiece(poses, buffers, light, overlay);
        poses.popPose();
    }

    private void renderPiece(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        switch (piece) {
            case HELMET -> {
                poses.scale(0.3125F, 0.3125F, 0.3125F);
                poses.translate(0.0D, 1.0D, 0.0D);
                render("Head", HELMET, true, poses, buffers, light, overlay);
            }
            case CHESTPLATE -> {
                poses.scale(0.225F, 0.225F, 0.225F);
                poses.translate(0.0D, -10.0D, 0.0D);
                render("Body", CHEST, true, poses, buffers, light, overlay);
                poses.translate(0.0D, 0.0D, 0.1D);
                render("LeftArm", ARM, true, poses, buffers, light, overlay);
                render("RightArm", ARM, true, poses, buffers, light, overlay);
            }
            case LEGGINGS -> {
                poses.scale(0.25F, 0.25F, 0.25F);
                poses.translate(0.0D, -20.0D, 0.0D);
                render("LeftLeg", LEG, false, poses, buffers, light, overlay);
                poses.translate(0.0D, 0.0D, 0.1D);
                render("RightLeg", LEG, false, poses, buffers, light, overlay);
            }
            case BOOTS -> {
                poses.scale(0.25F, 0.25F, 0.25F);
                poses.translate(0.0D, -22.0D, 0.0D);
                render("LeftBoot", LEG, false, poses, buffers, light, overlay);
                poses.translate(0.0D, 0.0D, 0.1D);
                render("RightBoot", LEG, false, poses, buffers, light, overlay);
            }
            default -> { }
        }
    }

    private void render(String group, ResourceLocation texture, boolean cull, PoseStack poses,
                        MultiBufferSource buffers, int light, int overlay) {
        RenderType type = cull ? RenderType.entityCutout(texture) : RenderType.entityCutoutNoCull(texture);
        mesh().render(group, poses.last(), buffers.getBuffer(type), 1.0F, light, overlay, 0xFFFFFFFF);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(),
                MODEL, GROUPS, "DNT Nano Suit");
        return mesh;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "textures/armor/" + name + ".png");
    }
}
