package com.hbm.ntm.client;

import com.hbm.ntm.armor.DntArmorEvents;
import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.item.DntArmorItem;
import com.hbm.ntm.network.DntJetpackInputPayload;
import com.hbm.ntm.network.DntJetpackStatePayload;
import com.hbm.ntm.registry.ModItems;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ClientDntArmorEvents {
    public static final KeyMapping TOGGLE_JETPACK = new KeyMapping(
            "key.hbm.dnt_jetpack", GLFW.GLFW_KEY_C, "key.categories.hbm");
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.hbm.dnt_hud", GLFW.GLFW_KEY_V, "key.categories.hbm");

    private static final RenderType THERMAL_LINES = RenderType.create(
            "hbm_dnt_thermal_lines",
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

    private static final List<HazardProtection> FULL_PACKAGE = List.of(
            HazardProtection.PARTICLE_COARSE, HazardProtection.PARTICLE_FINE,
            HazardProtection.GAS_LUNG, HazardProtection.BACTERIA,
            HazardProtection.GAS_BLISTERING, HazardProtection.GAS_MONOXIDE,
            HazardProtection.LIGHT, HazardProtection.SAND);
    private static final Map<Player, Boolean> PREVIOUS_HAT_VISIBILITY = new WeakHashMap<>();
    private static boolean jetpackEnabled = true;
    private static boolean hudEnabled = true;
    private static boolean lastJumpHeld;

    private ClientDntArmorEvents() {
    }

    static void register() {
        DntJetpackStatePayload.installClientHandler((jetpack, hud) -> {
            jetpackEnabled = jetpack;
            hudEnabled = hud;
        });
        NeoForge.EVENT_BUS.addListener(ClientDntArmorEvents::clientTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                ClientDntArmorEvents::beforePlayerRender);
        NeoForge.EVENT_BUS.addListener(ClientDntArmorEvents::afterPlayerRender);
        NeoForge.EVENT_BUS.addListener(ClientDntArmorEvents::appendTooltip);
        NeoForge.EVENT_BUS.addListener(ClientDntArmorEvents::renderThermalSight);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            lastJumpHeld = false;
            return;
        }

        boolean toggleJetpack = false;
        boolean toggleHud = false;
        while (TOGGLE_JETPACK.consumeClick()) toggleJetpack = true;
        while (TOGGLE_HUD.consumeClick()) toggleHud = true;

        boolean jumpHeld = minecraft.screen == null && minecraft.options.keyJump.isDown()
                && DntArmorItem.hasFullPoweredSet(minecraft.player);
        if (toggleJetpack || toggleHud || jumpHeld != lastJumpHeld) {
            PacketDistributor.sendToServer(new DntJetpackInputPayload(
                    jumpHeld, toggleJetpack, toggleHud));
            lastJumpHeld = jumpHeld;
        }
        if (DntArmorItem.hasFullPoweredSet(minecraft.player)) {
            DntArmorEvents.applyJetpackMovement(minecraft.player, jetpackEnabled, jumpHeld);
        }
    }

    public static boolean hudEnabled() {
        return hudEnabled;
    }

    private static void renderHealthBar(net.minecraft.world.entity.LivingEntity entity,
                                        Minecraft minecraft, PoseStack poses,
                                        MultiBufferSource buffers, net.minecraft.world.phys.Vec3 camera) {
        int count = Math.min((int) entity.getMaxHealth(), 100);
        if (count <= 0) return;
        int filled = (int) Math.ceil(entity.getHealth() * count / entity.getMaxHealth());
        Component bar = Component.literal("|".repeat(Math.max(0, filled))).withStyle(ChatFormatting.RED)
                .append(Component.literal("|".repeat(Math.max(0, count - filled))).withStyle(ChatFormatting.GRAY));

        poses.pushPose();
        poses.translate(entity.getX() - camera.x,
                entity.getY() + entity.getBbHeight() + 0.75D - camera.y,
                entity.getZ() - camera.z);
        poses.mulPose(minecraft.gameRenderer.getMainCamera().rotation());
        poses.scale(0.025F, -0.025F, 0.025F);
        Font font = minecraft.font;
        float x = -font.width(bar) / 2.0F;
        font.drawInBatch(bar, x, 0.0F, 0xFFFFFFFF, false, poses.last().pose(),
                buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        poses.popPose();
    }

    private static void renderThermalSight(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!hudActive(minecraft)) return;
        var player = minecraft.player;
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
        boolean renderedText = false;
        for (Entity entity : nearby) {
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) continue;
            renderHealthBar(living, minecraft, poses, buffers, camera);
            renderedText = true;
        }
        if (renderedText) buffers.endBatch();
    }

    private static float[] thermalColor(Entity entity, int ticks) {
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) return new float[] {1.0F, 0.5F, 0.0F};
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

    private static boolean hudActive(Minecraft minecraft) {
        return hudEnabled && minecraft.player != null
                && DntArmorItem.hasFullPoweredSet(minecraft.player);
    }

    private static void beforePlayerRender(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.DNS_HELMET.get())) return;
        boolean previous = event.getRenderer().getModel().hat.visible;
        PREVIOUS_HAT_VISIBILITY.put(player, previous);
        event.getRenderer().getModel().hat.visible = false;
    }

    private static void afterPlayerRender(RenderPlayerEvent.Post event) {
        Boolean previous = PREVIOUS_HAT_VISIBILITY.remove(event.getEntity());
        if (previous != null) event.getRenderer().getModel().hat.visible = previous;
    }

    private static void appendTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof DntArmorItem dnt)) return;
        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.translatable("damage.inset").withStyle(ChatFormatting.DARK_PURPLE));
        addSetPiece(tooltip, ModItems.DNS_HELMET.get().getDefaultInstance());
        addSetPiece(tooltip, ModItems.DNS_PLATE.get().getDefaultInstance());
        addSetPiece(tooltip, ModItems.DNS_LEGS.get().getDefaultInstance());
        addSetPiece(tooltip, ModItems.DNS_BOOTS.get().getDefaultInstance());
        tooltip.add(Component.translatable("damage.category.PHYSICAL").append(": 1000.0/100%"));
        tooltip.add(Component.translatable("damage.category.EXPLOSION").append(": 100.0/99%"));
        tooltip.add(Component.translatable("damage.category.FIRE").append(": 0.0/100%"));
        tooltip.add(Component.translatable("damage.other").append(": 1000.0/100%"));

        if (dnt.getType() == ArmorItem.Type.HELMET) {
            if (Screen.hasShiftDown()) {
                tooltip.add(Component.translatable("hazard.prot").withStyle(ChatFormatting.GOLD));
                for (HazardProtection protection : FULL_PACKAGE) {
                    tooltip.add(Component.literal("  ")
                            .append(Component.translatable(protection.translationKey()))
                            .withStyle(ChatFormatting.YELLOW));
                }
            } else {
                tooltip.add(Component.translatable("armor.dnt.hold_shift")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
        tooltip.add(Component.translatable("trait.radResistance",
                Float.toString(dnt.radiationResistance())).withStyle(ChatFormatting.YELLOW));
    }

    private static void addSetPiece(List<Component> tooltip, ItemStack stack) {
        tooltip.add(Component.literal("  ").append(stack.getHoverName()).withStyle(ChatFormatting.DARK_PURPLE));
    }
}
