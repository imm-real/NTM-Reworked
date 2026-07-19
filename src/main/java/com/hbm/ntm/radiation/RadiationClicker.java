package com.hbm.ntm.radiation;

import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Arrays;

/** Click-level selection shared by carried and placed radiation instruments. */
public final class RadiationClicker {
    public static final float ACTIVE_EPSILON = 1.0E-5F;

    private RadiationClicker() {
    }

    public static int[] geigerCandidates(float radiationPerSecond) {
        int[] candidates = new int[8];
        int size = 0;
        if (radiationPerSecond < 1.0F) candidates[size++] = 0;
        if (radiationPerSecond < 5.0F) candidates[size++] = 0;
        if (radiationPerSecond < 10.0F) candidates[size++] = 1;
        if (radiationPerSecond > 5.0F && radiationPerSecond < 15.0F) candidates[size++] = 2;
        if (radiationPerSecond > 10.0F && radiationPerSecond < 20.0F) candidates[size++] = 3;
        if (radiationPerSecond > 15.0F && radiationPerSecond < 25.0F) candidates[size++] = 4;
        if (radiationPerSecond > 20.0F && radiationPerSecond < 30.0F) candidates[size++] = 5;
        if (radiationPerSecond > 25.0F) candidates[size++] = 6;
        return Arrays.copyOf(candidates, size);
    }

    public static int[] dosimeterCandidates(float radiationPerSecond) {
        int[] candidates = new int[4];
        int size = 0;
        if (radiationPerSecond < 0.5F) candidates[size++] = 0;
        if (radiationPerSecond < 1.0F) candidates[size++] = 1;
        if (radiationPerSecond >= 0.5F && radiationPerSecond < 2.0F) candidates[size++] = 2;
        // "x >= 1 && x >= 2" reduces to x >= 2. Keep the silly threshold.
        if (radiationPerSecond >= 1.0F && radiationPerSecond >= 2.0F) candidates[size++] = 3;
        return Arrays.copyOf(candidates, size);
    }

    public static void tickCarriedGeiger(Level level, Entity entity, float radiationPerSecond) {
        tick(level, entity.blockPosition(), entity, radiationPerSecond, 50, false);
    }

    public static void tickCarriedDosimeter(Level level, Entity entity, float radiationPerSecond) {
        tick(level, entity.blockPosition(), entity, radiationPerSecond, 100, true);
    }

    public static void tickPlacedGeiger(Level level, BlockPos position, float radiationPerSecond) {
        tick(level, position, null, radiationPerSecond, 50, false);
    }

    private static void tick(
            Level level,
            BlockPos position,
            Entity entity,
            float radiationPerSecond,
            int quietOneIn,
            boolean dosimeter
    ) {
        RandomSource random = level.random;
        int click = 0;
        if (radiationPerSecond > ACTIVE_EPSILON) {
            int[] candidates = dosimeter
                    ? dosimeterCandidates(radiationPerSecond)
                    : geigerCandidates(radiationPerSecond);
            if (candidates.length > 0) click = candidates[random.nextInt(candidates.length)];
        } else if (random.nextInt(quietOneIn) == 0) {
            click = 1;
        }

        if (click <= 0) return;
        SoundEvent sound = sound(click);
        SoundSource source = entity == null ? SoundSource.BLOCKS : SoundSource.PLAYERS;
        if (entity == null) {
            level.playSound(null, position, sound, source, 1.0F, 1.0F);
        } else {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, source, 1.0F, 1.0F);
        }
    }

    public static SoundEvent sound(int level) {
        return switch (level) {
            case 1 -> ModSounds.GEIGER_1.get();
            case 2 -> ModSounds.GEIGER_2.get();
            case 3 -> ModSounds.GEIGER_3.get();
            case 4 -> ModSounds.GEIGER_4.get();
            case 5 -> ModSounds.GEIGER_5.get();
            case 6 -> ModSounds.GEIGER_6.get();
            default -> throw new IllegalArgumentException("Unknown Geiger click level: " + level);
        };
    }
}
