package com.hbm.ntm.worldgen;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** 1.7.10 NoiseGeneratorPerlin's two-dimensional octave sampler. */
public final class LegacyOctaveNoise2D {
    private final SimplexNoise[] noise;

    public LegacyOctaveNoise2D(long seed, int octaves) {
        noise = new SimplexNoise[octaves];
        LegacyRandomSource random = new LegacyRandomSource(seed);
        for (int octave = 0; octave < noise.length; octave++) noise[octave] = new SimplexNoise(random);
    }

    public double value(double x, double z) {
        double value = 0.0D;
        double frequency = 1.0D;
        for (SimplexNoise octave : noise) {
            value += octave.getValue(x * frequency, z * frequency) / frequency;
            frequency /= 2.0D;
        }
        return value;
    }
}
