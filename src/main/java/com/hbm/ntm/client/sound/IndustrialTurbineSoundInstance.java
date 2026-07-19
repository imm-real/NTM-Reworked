package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.IndustrialTurbineBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Source large-turbine loop with spin-controlled volume/pitch and a 35-block cutoff. */
public final class IndustrialTurbineSoundInstance extends AbstractTickableSoundInstance {
    private static final double RANGE_SQUARED = 35D * 35D;
    private final IndustrialTurbineBlockEntity turbine;
    private final float desync;

    public IndustrialTurbineSoundInstance(IndustrialTurbineBlockEntity turbine) {
        super(ModSounds.TURBINE_LARGE_LOOP.get(), SoundSource.BLOCKS, RandomSource.create());
        this.turbine = turbine;
        desync = random.nextFloat() * 0.05F;
        looping = true;
        delay = 0;
        attenuation = Attenuation.LINEAR;
        x = turbine.getBlockPos().getX() + 0.5D;
        y = turbine.getBlockPos().getY() + 0.5D;
        z = turbine.getBlockPos().getZ() + 0.5D;
        updateMix();
    }

    @Override public void tick() {
        if (turbine.isRemoved() || turbine.spin() <= 0D || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) > RANGE_SQUARED) {
            stop();
            return;
        }
        updateMix();
    }

    private void updateMix() {
        float spin = (float) Math.min(1D, turbine.spin() * 2D);
        volume = 0.25F + spin * 0.75F;
        pitch = 0.5F + spin * 0.5F + desync;
    }
}
