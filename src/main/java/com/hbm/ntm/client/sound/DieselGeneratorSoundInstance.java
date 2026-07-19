package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.DieselGeneratorBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class DieselGeneratorSoundInstance extends AbstractTickableSoundInstance {
    private final DieselGeneratorBlockEntity generator;

    public DieselGeneratorSoundInstance(DieselGeneratorBlockEntity generator) {
        super(ModSounds.ENGINE.get(), SoundSource.BLOCKS, RandomSource.create());
        this.generator = generator;
        looping = true;
        delay = 0;
        volume = 1F;
        pitch = 1F;
        attenuation = Attenuation.LINEAR;
        x = generator.getBlockPos().getX() + 0.5D;
        y = generator.getBlockPos().getY() + 0.5D;
        z = generator.getBlockPos().getZ() + 0.5D;
    }

    @Override public void tick() {
        if (generator.isRemoved() || !generator.active() || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) >= 100D) stop();
    }
}
