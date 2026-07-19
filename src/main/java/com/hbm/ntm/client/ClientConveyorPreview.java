package com.hbm.ntm.client;

import com.hbm.ntm.item.ConveyorWandItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Cyan/red route preview for the two-click conveyor builder. */
public final class ClientConveyorPreview {
    private ClientConveyorPreview() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientConveyorPreview::renderLevel);
    }

    private static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        ItemStack held = minecraft.player.getMainHandItem();
        ConveyorWandItem wand;
        if (held.getItem() instanceof ConveyorWandItem mainHandWand) {
            wand = mainHandWand;
        } else {
            held = minecraft.player.getOffhandItem();
            if (!(held.getItem() instanceof ConveyorWandItem offhandWand)) return;
            wand = offhandWand;
        }

        ConveyorWandItem.Route route = wand.previewRoute(minecraft.level, held,
                minecraft.player.getDirection(), hit);
        if (route == null) return;

        boolean valid = route.status() == ConveyorWandItem.RouteStatus.SUCCESS;
        float red = valid ? 0F : 1F;
        float green = valid ? 1F : 0F;
        float blue = valid ? 1F : 0F;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer blocks = buffers.getBuffer(RenderType.translucent());
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        ModelBlockRenderer renderer = dispatcher.getModelRenderer();

        for (ConveyorWandItem.Placement placement : route.placements()) {
            poses.pushPose();
            poses.translate(placement.pos().getX() - camera.x,
                    placement.pos().getY() - camera.y,
                    placement.pos().getZ() - camera.z);
            BakedModel model = dispatcher.getBlockModel(placement.state());
            renderer.renderModel(poses.last(), blocks, placement.state(), model,
                    red, green, blue, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poses.popPose();
        }
        buffers.endBatch(RenderType.translucent());

        if (route.obstruction() != null) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            AABB obstruction = new AABB(route.obstruction()).inflate(0.002D)
                    .move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(poses, lines, obstruction, 1F, 0F, 0F, 1F);
            buffers.endBatch(RenderType.lines());
        }
    }
}
