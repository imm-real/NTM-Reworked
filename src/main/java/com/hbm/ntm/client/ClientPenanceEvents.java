package com.hbm.ntm.client;

import com.hbm.ntm.item.PenanceItem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/** Penance thermal sight: glowing entity boxes with no respect for depth. */
public final class ClientPenanceEvents {
    private static final RenderType THERMAL_LINES = RenderType.create(
            "hbm_penance_thermal_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1_536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(RenderStateShard.DEFAULT_LINE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private ClientPenanceEvents() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientPenanceEvents::renderThermalSight);
    }

    private static void renderThermalSight(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof PenanceItem)) return;

        var camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(THERMAL_LINES);
        List<Entity> nearby = player.level().getEntities(player,
                        player.getBoundingBox().inflate(64.0D), candidate -> candidate != player)
                .stream().filter(entity -> entity.distanceToSqr(player) <= 4096.0D).toList();
        for (Entity entity : nearby) {
            float[] color = thermalColor(entity, player.tickCount);
            if (color == null) continue;
            if (entity instanceof net.minecraft.world.entity.LivingEntity living && living.getHealth() <= 0.0F) {
                color = new float[] {0.0F, 0.0F, 0.0F};
            }
            LevelRenderer.renderLineBox(poses, lines,
                    entity.getBoundingBox().move(-camera.x, -camera.y, -camera.z),
                    color[0], color[1], color[2], 1.0F);
        }
        buffers.endBatch(THERMAL_LINES);
    }

    private static float[] thermalColor(Entity entity, int ticks) {
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) {
            return new float[] {1.0F, 0.5F, 0.0F};
        }
        if (entity instanceof Enemy) return new float[] {1.0F, 0.0F, 0.0F};
        if (entity instanceof Player) return new float[] {1.0F, 0.0F, 1.0F};
        if (entity instanceof Mob) return new float[] {0.0F, 1.0F, 0.0F};
        if (entity instanceof ItemEntity) return new float[] {1.0F, 1.0F, 0.5F};
        if (entity instanceof ExperienceOrb) {
            return ticks % 10 < 5 ? new float[] {1.0F, 1.0F, 0.5F}
                    : new float[] {0.5F, 1.0F, 0.5F};
        }
        return null;
    }
}
