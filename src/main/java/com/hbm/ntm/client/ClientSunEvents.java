package com.hbm.ntm.client;

import com.hbm.ntm.network.SunStatePayload;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.world.SunDestruction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client timing, camera, flash, sound, and permanent blackout for the B93 solar event. */
public final class ClientSunEvents {
    public static final int FLIGHT_START = 6;
    public static final int FLIGHT_END = 48;
    public static final int IMPACT_TICK = 72;
    public static final int EXPLOSION_DURATION = 300;
    public static final int IGNITION_TICK = 10;
    public static final int PHOTOSPHERE_TICK = 28;
    public static final int HYPERNOVA_TICK = 56;
    public static final int RUPTURE_TICK = 96;
    public static final int AFTERSHOCK_TICK = 150;
    private static final int FOCUS_IN_DURATION = 18;
    private static final int FOCUS_OUT_DURATION = 185;
    private static final int SHAKE_DURATION = 245;
    private static final int INITIAL_FLASH_DURATION = 62;

    private static boolean destroyed;
    private static int approachAge = IMPACT_TICK;
    private static int explosionAge = EXPLOSION_DURATION;
    private static float focusYaw = Float.NaN;

    private ClientSunEvents() {
    }

    public static void register() {
        SunStatePayload.installClientHandler(ClientSunEvents::receiveState);
        NeoForge.EVENT_BUS.addListener(ClientSunEvents::clientTick);
        NeoForge.EVENT_BUS.addListener(ClientSunEvents::cameraAngles);
        NeoForge.EVENT_BUS.addListener(ClientSunEvents::fov);
        NeoForge.EVENT_BUS.addListener(ClientSunEvents::renderFlash);
        NeoForge.EVENT_BUS.addListener(ClientSunEvents::loggingOut);
        SolarImpactProjectileRenderer.register();
    }

    private static void receiveState(boolean newDestroyed, boolean explode) {
        destroyed = newDestroyed;
        if (explode) {
            approachAge = 0;
            explosionAge = EXPLOSION_DURATION;
            focusYaw = Float.NaN;
            play(ModSounds.GUN_B92_FIRE.get(), 0.48F, 5.0F);
        } else {
            approachAge = IMPACT_TICK;
            explosionAge = EXPLOSION_DURATION;
            focusYaw = Float.NaN;
        }
    }

    private static void clientTick(ClientTickEvent.Post event) {
        if (approachAge < IMPACT_TICK) {
            approachAge++;
            if (approachAge == IMPACT_TICK) startExplosion();
            return;
        }
        if (explosionAge >= EXPLOSION_DURATION) return;
        explosionAge++;
        if (explosionAge == IGNITION_TICK) {
            playLayered(ModSounds.EXPLOSION_LARGE_NEAR.get(), 0.34F, 18.0F, 10, 0.014F);
            playLayered(SoundEvents.GENERIC_EXPLODE.value(), 0.52F, 16.0F, 7, 0.022F);
        } else if (explosionAge == PHOTOSPHERE_TICK) {
            playLayered(ModSounds.NUCLEAR_EXPLOSION.get(), 0.44F, 20.0F, 9, 0.013F);
            playLayered(ModSounds.EXPLOSION_SMALL_NEAR.get(), 0.60F, 17.0F, 9, 0.018F);
            playLayered(ModSounds.EXPLOSION_LARGE_FAR.get(), 0.34F, 20.0F, 6, 0.014F);
        } else if (explosionAge == HYPERNOVA_TICK) {
            playLayered(ModSounds.EXPLOSION_LARGE_NEAR.get(), 0.24F, 24.0F, 12, 0.012F);
            playLayered(ModSounds.NUCLEAR_EXPLOSION.get(), 0.34F, 24.0F, 12, 0.011F);
            playLayered(SoundEvents.GENERIC_EXPLODE.value(), 0.42F, 20.0F, 8, 0.018F);
        } else if (explosionAge == RUPTURE_TICK) {
            playLayered(ModSounds.EXPLOSION_LARGE_FAR.get(), 0.18F, 26.0F, 14, 0.010F);
            playLayered(ModSounds.EXPLOSION_LARGE_NEAR.get(), 0.28F, 24.0F, 12, 0.011F);
            playLayered(ModSounds.NUCLEAR_EXPLOSION.get(), 0.30F, 24.0F, 10, 0.012F);
        } else if (explosionAge == AFTERSHOCK_TICK) {
            playLayered(ModSounds.EXPLOSION_LARGE_FAR.get(), 0.14F, 24.0F, 12, 0.009F);
            playLayered(ModSounds.NUCLEAR_EXPLOSION.get(), 0.22F, 20.0F, 8, 0.011F);
        }
    }

    private static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) return;

        float partialTick = (float) event.getPartialTick();
        float focus = focusAmount(partialTick);
        if (focus > 0.0F) {
            if (Float.isNaN(focusYaw)) focusYaw = event.getYaw();
            Vec3 sun = SunDestruction.sunDirection(minecraft.level);
            double horizontal = Math.hypot(sun.x, sun.z);
            float targetYaw = horizontal < 0.20D
                    ? focusYaw
                    : (float) (Mth.atan2(-sun.x, sun.z) * Mth.RAD_TO_DEG);
            float targetPitch = (float) (Mth.atan2(-sun.y, horizontal) * Mth.RAD_TO_DEG);
            event.setYaw(Mth.rotLerp(focus, event.getYaw(), targetYaw));
            event.setPitch(Mth.lerp(focus, event.getPitch(), targetPitch));

            if (isApproachActive() && approachAge >= FLIGHT_END) {
                float tension = Mth.clamp((approachAge + partialTick - FLIGHT_END)
                        / (IMPACT_TICK - FLIGHT_END), 0.0F, 1.0F);
                event.setRoll(event.getRoll() + Mth.sin((approachAge + partialTick) * 1.9F)
                        * tension * 0.52F);
            }
        }

        if (explosionAge < SHAKE_DURATION) {
            float age = explosionAge + partialTick;
            float strength = 11.0F * square(1.0F - age / SHAKE_DURATION)
                    + shockPulse(age, IGNITION_TICK, 28.0F, 4.2F)
                    + shockPulse(age, PHOTOSPHERE_TICK, 42.0F, 6.0F)
                    + shockPulse(age, HYPERNOVA_TICK, 64.0F, 9.5F)
                    + shockPulse(age, RUPTURE_TICK, 82.0F, 12.5F)
                    + shockPulse(age, AFTERSHOCK_TICK, 74.0F, 7.5F);
            event.setYaw(event.getYaw() + (float) Math.sin(age * 2.17D) * strength);
            event.setPitch(event.getPitch() + (float) Math.sin(age * 1.61D + 0.8D) * strength * 0.74F);
            event.setRoll(event.getRoll() + (float) Math.sin(age * 2.71D + 1.4D) * strength * 1.35F);
        }
    }

    private static void fov(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) return;
        float focus = focusAmount((float) event.getPartialTick());
        if (focus > 0.0F) event.setFOV(event.getFOV() * (1.0D - focus * 0.40D));
    }

    private static void renderFlash(RenderGuiEvent.Post event) {
        if (explosionAge >= EXPLOSION_DURATION) return;
        float age = explosionAge + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float initial = age < INITIAL_FLASH_DURATION
                ? square(1.0F - age / INITIAL_FLASH_DURATION)
                : 0.0F;
        float opacity = Math.max(initial,
                Math.max(flashPulse(age, IGNITION_TICK, 13.0F, 0.82F),
                        Math.max(flashPulse(age, PHOTOSPHERE_TICK, 19.0F, 0.92F),
                                Math.max(flashPulse(age, HYPERNOVA_TICK, 28.0F, 1.0F),
                                        Math.max(flashPulse(age, RUPTURE_TICK, 34.0F, 1.0F),
                                                flashPulse(age, AFTERSHOCK_TICK, 30.0F, 0.72F))))));
        opacity = Math.max(opacity,
                Math.max(strobePulse(age, IGNITION_TICK, 18.0F, 0.72F),
                        Math.max(strobePulse(age, PHOTOSPHERE_TICK, 26.0F, 0.78F),
                                Math.max(strobePulse(age, HYPERNOVA_TICK, 42.0F, 0.92F),
                                        Math.max(strobePulse(age, RUPTURE_TICK, 52.0F, 0.96F),
                                                strobePulse(age, AFTERSHOCK_TICK, 42.0F, 0.68F))))));
        int alpha = Mth.clamp(Math.round(255.0F * opacity), 0, 255);
        if (age > 4.0F) alpha = Math.min(alpha, 170);
        if (alpha == 0) return;
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | flashColor(age));
    }

    private static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        destroyed = false;
        approachAge = IMPACT_TICK;
        explosionAge = EXPLOSION_DURATION;
        focusYaw = Float.NaN;
    }

    private static void startExplosion() {
        explosionAge = 0;
        playLayered(SoundEvents.GENERIC_EXPLODE.value(), 0.36F, 24.0F, 8, 0.020F);
        playLayered(ModSounds.EXPLOSION_LARGE_NEAR.get(), 0.30F, 26.0F, 10, 0.014F);
        playLayered(ModSounds.NUCLEAR_EXPLOSION.get(), 0.42F, 24.0F, 8, 0.013F);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static void playLayered(SoundEvent sound, float pitch, float volume,
                                    int layers, float pitchSpacing) {
        float midpoint = (layers - 1) * 0.5F;
        for (int layer = 0; layer < layers; layer++) {
            play(sound, Math.max(0.1F, pitch + (layer - midpoint) * pitchSpacing), volume);
        }
    }

    private static float square(float value) {
        return value * value;
    }

    private static float shockPulse(float age, float start, float duration, float strength) {
        float localAge = age - start;
        if (localAge < 0.0F || localAge >= duration) return 0.0F;
        float attack = Mth.clamp(localAge / 1.5F, 0.0F, 1.0F);
        float decay = 1.0F - localAge / duration;
        return strength * attack * decay * decay;
    }

    private static float flashPulse(float age, float start, float duration, float strength) {
        float localAge = age - start;
        if (localAge < 0.0F || localAge >= duration) return 0.0F;
        float attack = Mth.clamp(localAge, 0.0F, 1.0F);
        float decay = 1.0F - localAge / duration;
        return strength * attack * decay * decay;
    }

    private static float strobePulse(float age, float start, float duration, float strength) {
        float localAge = age - start;
        if (localAge < 0.0F || localAge >= duration) return 0.0F;
        float attack = Mth.clamp(localAge / 1.5F, 0.0F, 1.0F);
        float decay = 1.0F - localAge / duration;
        float strobe = 0.18F + 0.82F * Math.abs(Mth.sin(localAge * 1.72F));
        return strength * attack * decay * decay * strobe;
    }

    private static int flashColor(float age) {
        int flicker = ((int) Math.floor(age * 0.72F)) & 1;
        if (age < PHOTOSPHERE_TICK) return flicker == 0 ? 0xFFF7C7 : 0xB8FFFF;
        if (age < HYPERNOVA_TICK) return flicker == 0 ? 0xFF8AD8 : 0xFFD35A;
        if (age < RUPTURE_TICK) return flicker == 0 ? 0xB6A0FF : 0x78E8FF;
        if (age < AFTERSHOCK_TICK) return flicker == 0 ? 0xFF537F : 0xA4FF6E;
        return flicker == 0 ? 0x65B9FF : 0xE68CFF;
    }

    public static boolean isDestroyed() {
        return destroyed;
    }

    public static boolean isApproachActive() {
        return destroyed && approachAge < IMPACT_TICK;
    }

    public static boolean isExploding() {
        return explosionAge < EXPLOSION_DURATION;
    }

    public static float explosionAge(float partialTick) {
        return Math.min(EXPLOSION_DURATION, explosionAge + partialTick);
    }

    public static float approachAge(float partialTick) {
        return Math.min(IMPACT_TICK, approachAge + partialTick);
    }

    public static float flightProgress(float partialTick) {
        float linear = Mth.clamp((approachAge(partialTick) - FLIGHT_START) / (FLIGHT_END - FLIGHT_START),
                0.0F, 1.0F);
        return smoothStep(linear);
    }

    public static float holdProgress(float partialTick) {
        return Mth.clamp((approachAge(partialTick) - FLIGHT_END) / (IMPACT_TICK - FLIGHT_END),
                0.0F, 1.0F);
    }

    public static Vec3 projectileDirection(net.minecraft.client.multiplayer.ClientLevel level,
                                           float progress, float fallbackYaw) {
        Vec3 sun = SunDestruction.sunDirection(level);
        double horizontalLength = Math.hypot(sun.x, sun.z);
        Vec3 horizontal;
        if (horizontalLength < 0.20D) {
            float yaw = Float.isNaN(focusYaw) ? fallbackYaw : focusYaw;
            float radians = yaw * Mth.DEG_TO_RAD;
            horizontal = new Vec3(-Mth.sin(radians), 0.0D, Mth.cos(radians));
        } else {
            horizontal = new Vec3(sun.x / horizontalLength, 0.0D, sun.z / horizontalLength);
        }

        double targetElevation = Math.asin(Mth.clamp(sun.y, -1.0D, 1.0D));
        double startElevation = Math.max(Math.toRadians(8.0D), targetElevation - Math.toRadians(65.0D));
        Vec3 start = horizontal.scale(Math.cos(startElevation)).add(0.0D, Math.sin(startElevation), 0.0D);
        return start.scale(1.0D - progress).add(sun.scale(progress)).normalize();
    }

    private static float focusAmount(float partialTick) {
        if (isApproachActive()) {
            return smoothStep(Mth.clamp(approachAge(partialTick) / FOCUS_IN_DURATION, 0.0F, 1.0F));
        }
        if (explosionAge < FOCUS_OUT_DURATION) {
            return 1.0F - smoothStep(Mth.clamp((explosionAge + partialTick) / FOCUS_OUT_DURATION,
                    0.0F, 1.0F));
        }
        return 0.0F;
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    public static boolean shouldBlacken(net.minecraft.client.multiplayer.ClientLevel level) {
        return destroyed && !isApproachActive() && level.dimension() == Level.OVERWORLD;
    }
}
