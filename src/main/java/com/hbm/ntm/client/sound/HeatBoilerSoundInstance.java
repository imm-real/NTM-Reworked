package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.HeatBoilerBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class HeatBoilerSoundInstance extends AbstractTickableSoundInstance {
    private final HeatBoilerBlockEntity boiler;

    public HeatBoilerSoundInstance(HeatBoilerBlockEntity boiler) {
        super(ModSounds.BOILER.get(), SoundSource.BLOCKS, RandomSource.create());
        this.boiler = boiler;
        looping = true;
        delay = 0;
        volume = 0.125F;
        pitch = 1F;
        attenuation = Attenuation.LINEAR;
        x = boiler.getBlockPos().getX() + 0.5D;
        y = boiler.getBlockPos().getY() + 0.5D;
        z = boiler.getBlockPos().getZ() + 0.5D;
    }

    @Override public void tick() {
        if (boiler.isRemoved() || boiler.hasExploded() || !boiler.active()
                || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) >= 10D * 10D) stop();
    }
}
