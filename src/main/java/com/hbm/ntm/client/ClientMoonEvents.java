package com.hbm.ntm.client;

import com.hbm.ntm.network.MoonStatePayload;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.world.MoonDestruction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client state, sound, flash, shake, and Overworld renderer for the destroyed moon. */
public final class ClientMoonEvents {
    public static final int FLIGHT_START = 6;
    public static final int FLIGHT_END = 48;
    public static final int IMPACT_TICK = 72;
    public static final int EXPLOSION_DURATION = 140;
    public static final int SECONDARY_BURST_TICK = 14;
    public static final int CORE_REIGNITION_TICK = 34;
    public static final int RUPTURE_WAVE_TICK = 58;
    private static final int FOCUS_IN_DURATION = 18;
    private static final int FOCUS_OUT_DURATION = 82;
    private static final int SHAKE_DURATION = 116;
    private static final int INITIAL_FLASH_DURATION = 30;

    private static boolean destroyed;
    private static int approachAge = IMPACT_TICK;
    private static int explosionAge = EXPLOSION_DURATION;
    private static float focusYaw = Float.NaN;

    private ClientMoonEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientMoonEvents::registerDimensionEffects);
        MoonStatePayload.installClientHandler(ClientMoonEvents::receiveState);
        NeoForge.EVENT_BUS.addListener(ClientMoonEvents::clientTick);
        NeoForge.EVENT_BUS.addListener(ClientMoonEvents::cameraAngles);
        NeoForge.EVENT_BUS.addListener(ClientMoonEvents::fov);
        NeoForge.EVENT_BUS.addListener(ClientMoonEvents::renderFlash);
        NeoForge.EVENT_BUS.addListener(ClientMoonEvents::loggingOut);
        MoonImpactProjectileRenderer.register();
        MoonDebrisShower.register();
    }

    private static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD_EFFECTS,
                new MoonlessOverworldEffects());
    }

    private static void receiveState(boolean newDestroyed, boolean explode) {
        destroyed = newDestroyed;
        if (explode) {
            approachAge = 0;
            explosionAge = EXPLOSION_DURATION;
            focusYaw = Float.NaN;
            play(ModSounds.GUN_B92_FIRE.get(), 0.72F, 3.0F);
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
        if (explosionAge == 9) {
            playLayered(ModSounds.EXPLOSION_LARGE_NEAR.get(), 0.48F, 10.0F, 4, 0.025F);
        } else if (explosionAge == SECONDARY_BURST_TICK) {
            playLayered(ModSounds.EXPLOSION_SMALL_NEAR.get(), 0.68F, 8.0F, 5, 0.030F);
            playLayered(SoundEvents.GENERIC_EXPLODE.value(), 0.72F, 7.0F, 3, 0.040F);
        } else if (explosionAge == 24) {
            playLayered(ModSounds.EXPLOSION_LARGE_FAR.get(), 0.62F, 8.0F, 4, 0.025F);
        } else if (explosionAge == CORE_REIGNITION_TICK) {
            playLayered(ModSounds.EXPLOSION_LARGE_NEAR.get(), 0.36F, 11.0F, 7, 0.018F);
            playLayered(SoundEvents.GENERIC_EXPLODE.value(), 0.44F, 9.0F, 4, 0.030F);
            playLayered(ModSounds.NUCLEAR_EXPLOSION.get(), 0.60F, 8.0F, 2, 0.025F);
        } else if (explosionAge == RUPTURE_WAVE_TICK) {
            playLayered(ModSounds.EXPLOSION_LARGE_FAR.get(), 0.42F, 11.0F, 8, 0.018F);
            playLayered(ModSounds.EXPLOSION_SMALL_FAR.get(), 0.30F, 8.0F, 5, 0.025F);
            playLayered(ModSounds.EXPLOSION_LARGE_NEAR.get(), 0.34F, 11.0F, 4, 0.020F);
            playLayered(ModSounds.NUCLEAR_EXPLOSION.get(), 0.50F, 10.0F, 3, 0.020F);
        }
    }

    private static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) return;

        float partialTick = (float) event.getPartialTick();
        float focus = focusAmount(partialTick);
        if (focus > 0.0F) {
            if (Float.isNaN(focusYaw)) focusYaw = event.getYaw();
            Vec3 moon = MoonDestruction.moonDirection(minecraft.level);
            double horizontal = Math.hypot(moon.x, moon.z);
            float targetYaw = horizontal < 0.20D
                    ? focusYaw
                    : (float) (Mth.atan2(-moon.x, moon.z) * Mth.RAD_TO_DEG);
            float targetPitch = (float) (Mth.atan2(-moon.y, horizontal) * Mth.RAD_TO_DEG);
            event.setYaw(Mth.rotLerp(focus, event.getYaw(), targetYaw));
            event.setPitch(Mth.lerp(focus, event.getPitch(), targetPitch));

            if (isApproachActive() && approachAge >= FLIGHT_END) {
                float tension = Mth.clamp((approachAge + partialTick - FLIGHT_END)
                        / (IMPACT_TICK - FLIGHT_END), 0.0F, 1.0F);
                event.setRoll(event.getRoll() + Mth.sin((approachAge + partialTick) * 1.7F) * tension * 0.35F);
            }
        }

        if (explosionAge < SHAKE_DURATION) {
            float age = explosionAge + partialTick;
            float strength = 7.5F * square(1.0F - age / SHAKE_DURATION)
                    + shockPulse(age, SECONDARY_BURST_TICK, 24.0F, 2.8F)
                    + shockPulse(age, CORE_REIGNITION_TICK, 34.0F, 4.4F)
                    + shockPulse(age, RUPTURE_WAVE_TICK, 44.0F, 5.8F);
            event.setYaw(event.getYaw() + (float) Math.sin(age * 2.31D) * strength);
            event.setPitch(event.getPitch() + (float) Math.sin(age * 1.73D + 0.8D) * strength * 0.72F);
            event.setRoll(event.getRoll() + (float) Math.sin(age * 2.87D + 1.4D) * strength * 1.25F);
        }
    }

    private static void fov(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) return;
        float focus = focusAmount((float) event.getPartialTick());
        if (focus > 0.0F) event.setFOV(event.getFOV() * (1.0D - focus * 0.30D));
    }

    private static void renderFlash(RenderGuiEvent.Post event) {
        if (explosionAge >= EXPLOSION_DURATION) return;
        float age = explosionAge + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float initial = age < INITIAL_FLASH_DURATION
                ? square(1.0F - age / INITIAL_FLASH_DURATION)
                : 0.0F;
        float opacity = Math.max(initial,
                Math.max(flashPulse(age, SECONDARY_BURST_TICK, 10.0F, 0.42F),
                        Math.max(flashPulse(age, CORE_REIGNITION_TICK, 14.0F, 0.64F),
                                flashPulse(age, RUPTURE_WAVE_TICK, 18.0F, 0.78F))));
        int alpha = Mth.clamp(Math.round(255.0F * opacity), 0, 255);
        if (alpha == 0) return;
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | 0xFFF4D6);
    }

    private static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        destroyed = false;
        approachAge = IMPACT_TICK;
        explosionAge = EXPLOSION_DURATION;
        focusYaw = Float.NaN;
        MoonDebrisShower.reset();
    }

    private static void startExplosion() {
        explosionAge = 0;
        MoonDebrisShower.begin();
        playImmediateBlast();
    }

    private static void playImmediateBlast() {
        playLayered(SoundEvents.GENERIC_EXPLODE.value(), 0.50F, 12.0F, 4, 0.035F);
        playLayered(ModSounds.EXPLOSION_LARGE_NEAR.get(), 0.58F, 12.0F, 6, 0.025F);
        playLayered(ModSounds.NUCLEAR_EXPLOSION.get(), 0.78F, 8.0F, 3, 0.018F);
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static void playLayered(net.minecraft.sounds.SoundEvent sound, float pitch, float volume,
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
        float attack = Mth.clamp(localAge / 1.0F, 0.0F, 1.0F);
        float decay = 1.0F - localAge / duration;
        return strength * attack * decay * decay;
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
        Vec3 moon = MoonDestruction.moonDirection(level);
        double horizontalLength = Math.hypot(moon.x, moon.z);
        Vec3 horizontal;
        if (horizontalLength < 0.20D) {
            float yaw = Float.isNaN(focusYaw) ? fallbackYaw : focusYaw;
            float radians = yaw * Mth.DEG_TO_RAD;
            horizontal = new Vec3(-Mth.sin(radians), 0.0D, Mth.cos(radians));
        } else {
            horizontal = new Vec3(moon.x / horizontalLength, 0.0D, moon.z / horizontalLength);
        }

        double targetElevation = Math.asin(Mth.clamp(moon.y, -1.0D, 1.0D));
        double startElevation = Math.max(Math.toRadians(8.0D), targetElevation - Math.toRadians(65.0D));
        Vec3 start = horizontal.scale(Math.cos(startElevation)).add(0.0D, Math.sin(startElevation), 0.0D);
        return start.scale(1.0D - progress).add(moon.scale(progress)).normalize();
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
        return destroyed && !isApproachActive() && level.dimension() == Level.OVERWORLD
                && MoonDestruction.isBlackNight(level.getDayTime());
    }
}
