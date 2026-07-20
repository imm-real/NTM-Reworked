package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.GasTurbineBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class GasTurbineSoundInstance extends AbstractTickableSoundInstance {
    private static final double RANGE_SQUARED = 20D * 20D;
    private final GasTurbineBlockEntity turbine;

    public GasTurbineSoundInstance(GasTurbineBlockEntity turbine) {
        super(ModSounds.TURBINE_GAS_RUNNING.get(), SoundSource.BLOCKS, RandomSource.create());
        this.turbine = turbine;
        looping = true;
        delay = 0;
        attenuation = Attenuation.LINEAR;
        x = turbine.getBlockPos().getX() + 0.5D;
        y = turbine.getBlockPos().getY() + 1.5D;
        z = turbine.getBlockPos().getZ() + 0.5D;
        updateMix();
    }

    @Override public void tick() {
        if (turbine.isRemoved() || turbine.rpm() < 10 || turbine.state() == -1
                || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) > RANGE_SQUARED) {
            stop();
            return;
        }
        updateMix();
    }

    private void updateMix() {
        pitch = (float) (0.55D + 0.1D * turbine.rpm() / 10D);
        volume = Math.clamp(0.25F + turbine.rpm() / 100F * 0.75F, 0F, 1F);
    }
}
