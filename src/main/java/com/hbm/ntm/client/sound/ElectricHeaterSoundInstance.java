package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.ElectricHeaterBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class ElectricHeaterSoundInstance extends AbstractTickableSoundInstance {
    private static final double RANGE_SQUARED = 7.5D * 7.5D;
    private final ElectricHeaterBlockEntity heater;

    public ElectricHeaterSoundInstance(ElectricHeaterBlockEntity heater) {
        super(ModSounds.ELECTRIC_HUM.get(), SoundSource.BLOCKS, RandomSource.create());
        this.heater = heater;
        looping = true;
        delay = 0;
        volume = 0.25F;
        pitch = 1F;
        attenuation = Attenuation.LINEAR;
        x = heater.getBlockPos().getX() + 0.5D;
        y = heater.getBlockPos().getY() + 0.5D;
        z = heater.getBlockPos().getZ() + 0.5D;
    }

    @Override public void tick() {
        if (heater.isRemoved() || !heater.active() || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) >= RANGE_SQUARED) stop();
    }
}
