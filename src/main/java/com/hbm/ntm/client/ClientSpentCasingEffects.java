package com.hbm.ntm.client;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.network.SpentCasingPayload;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.SpentCasingPreset;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class ClientSpentCasingEffects {
    private static final ResourceLocation MODEL = id("models/effect/casings.obj");
    private static final ResourceLocation TEXTURE = id("textures/particle/casings.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE);
    private static final Set<String> GROUPS = Set.of("Straight", "Bottleneck", "Shotgun", "ShotgunCase");
    private static final List<Casing> CASINGS = new ArrayList<>();
    private static EnvsuitMesh mesh;

    private ClientSpentCasingEffects() { }

    public static void register() {
        SpentCasingPayload.installClientHandler(ClientSpentCasingEffects::spawn);
        NeoForge.EVENT_BUS.addListener(ClientSpentCasingEffects::tick);
        NeoForge.EVENT_BUS.addListener(ClientSpentCasingEffects::render);
    }

    private static void spawn(SpentCasingPayload payload) {
        SpentCasingPreset[] presets = SpentCasingPreset.values();
        if (payload.preset() < 0 || payload.preset() >= presets.length) return;
        CASINGS.add(new Casing(presets[payload.preset()], payload));
    }

    private static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            CASINGS.clear();
            return;
        }
        Iterator<Casing> iterator = CASINGS.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().tick(level)) iterator.remove();
        }
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CASINGS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);

        for (Casing casing : CASINGS) {
            Vec3 position = casing.oldPosition.lerp(casing.position, partial);
            if (!event.getFrustum().isVisible(AABB.ofSize(position, 0.5D, 0.5D, 0.5D))) continue;
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(position));
            poses.pushPose();
            poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            poses.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.lerp(partial, casing.oldYaw, casing.yaw)));
            poses.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, casing.oldPitch, casing.pitch)));
            poses.scale(0.05F * casing.preset.scaleX(), 0.05F * casing.preset.scaleY(),
                    0.05F * casing.preset.scaleZ());
            renderParts(casing.preset, poses, consumer, light);
            poses.popPose();
        }
        buffers.endBatch(RENDER_TYPE);
    }

    private static void renderParts(SpentCasingPreset preset, PoseStack poses,
                                    VertexConsumer consumer, int light) {
        int body = FastColor.ARGB32.opaque(preset.bodyColor());
        switch (preset.shape()) {
            case STRAIGHT -> mesh().render("Straight", poses.last(), consumer, 1.0F, light,
                    OverlayTexture.NO_OVERLAY, body);
            case BOTTLENECK -> mesh().render("Bottleneck", poses.last(), consumer, 1.0F, light,
                    OverlayTexture.NO_OVERLAY, body);
            case SHOTGUN -> {
                mesh().render("Shotgun", poses.last(), consumer, 1.0F, light,
                        OverlayTexture.NO_OVERLAY, body);
                mesh().render("ShotgunCase", poses.last(), consumer, 1.0F, light,
                        OverlayTexture.NO_OVERLAY, FastColor.ARGB32.opaque(preset.baseColor()));
            }
        }
    }

    private static EnvsuitMesh mesh() {
        if (mesh == null) {
            mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(), MODEL, GROUPS,
                    "spent casing");
        }
        return mesh;
    }

    private static SoundEvent bounceSound(SpentCasingPreset.Sound sound) {
        return switch (sound) {
            case SMALL -> ModSounds.CASING_SMALL.get();
            case MEDIUM -> ModSounds.CASING_MEDIUM.get();
            case LARGE -> ModSounds.CASING_LARGE.get();
            case SHELL -> ModSounds.CASING_SHELL.get();
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }

    private static final class Casing {
        private final SpentCasingPreset preset;
        private Vec3 oldPosition;
        private Vec3 position;
        private Vec3 velocity;
        private float oldYaw;
        private float yaw;
        private float oldPitch;
        private float pitch;
        private float momentumPitch;
        private float momentumYaw;
        private int age;

        private Casing(SpentCasingPreset preset, SpentCasingPayload payload) {
            this.preset = preset;
            this.position = new Vec3(payload.x(), payload.y(), payload.z());
            this.oldPosition = position;
            this.velocity = new Vec3(payload.motionX(), payload.motionY(), payload.motionZ());
            this.yaw = payload.yaw();
            this.oldYaw = yaw;
            this.pitch = payload.pitch();
            this.oldPitch = pitch;
            this.momentumPitch = payload.momentumPitch();
            this.momentumYaw = payload.momentumYaw();
        }

        private boolean tick(ClientLevel level) {
            if (age++ >= 240) return false;
            oldPosition = position;
            oldYaw = yaw;
            oldPitch = pitch;
            velocity = velocity.add(0.0D, -0.04D, 0.0D);

            double radius = 0.05D * Math.max(preset.scaleX(), preset.scaleZ());
            double height = 0.05D * preset.scaleY();
            AABB box = AABB.ofSize(position, radius * 2.0D, height, radius * 2.0D);
            boolean hitY = !level.noCollision(box.move(0.0D, velocity.y, 0.0D));
            if (hitY) {
                double falling = velocity.y;
                velocity = new Vec3(velocity.x, velocity.y * -0.5D, velocity.z);
                momentumPitch *= -0.75F;
                if (Math.abs(falling) >= 0.2D) {
                    float speed = Mth.clamp((float) (falling / 0.2D), -1.0F, 1.0F);
                    momentumPitch += (float) level.random.nextGaussian() * 10.0F * speed;
                    momentumYaw += (float) level.random.nextGaussian() * 10.0F * speed;
                    level.playLocalSound(position.x, position.y, position.z, bounceSound(preset.sound()),
                            SoundSource.PLAYERS, preset.sound() == SpentCasingPreset.Sound.LARGE ? 1.0F : 0.5F,
                            1.0F + level.random.nextFloat() * 0.2F, false);
                }
            } else {
                position = position.add(0.0D, velocity.y, 0.0D);
            }

            box = AABB.ofSize(position, radius * 2.0D, height, radius * 2.0D);
            if (!level.noCollision(box.move(velocity.x, 0.0D, 0.0D))) {
                velocity = new Vec3(velocity.x * -0.25D, velocity.y, velocity.z);
                momentumYaw = Math.abs(momentumYaw) > 1.0E-7F
                        ? momentumYaw * -0.75F : (float) level.random.nextGaussian() * 10.0F;
            } else {
                position = position.add(velocity.x, 0.0D, 0.0D);
            }

            box = AABB.ofSize(position, radius * 2.0D, height, radius * 2.0D);
            if (!level.noCollision(box.move(0.0D, 0.0D, velocity.z))) {
                velocity = new Vec3(velocity.x, velocity.y, velocity.z * -0.25D);
                momentumYaw = Math.abs(momentumYaw) > 1.0E-7F
                        ? momentumYaw * -0.75F : (float) level.random.nextGaussian() * 10.0F;
            } else {
                position = position.add(0.0D, 0.0D, velocity.z);
            }

            if (hitY && velocity.y > 0.0D) {
                velocity = new Vec3(velocity.x * 0.7D, velocity.y, velocity.z * 0.7D);
                pitch = Math.round(pitch / 180.0F) * 180.0F;
                momentumYaw *= 0.7F;
            }
            velocity = velocity.scale(0.98D);
            pitch += momentumPitch;
            yaw += momentumYaw;
            return true;
        }
    }
}
