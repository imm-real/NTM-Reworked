package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.FensuBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Source FEnSU loop with flywheel-speed pitch and a 30-block player cutoff. */
public final class FensuSoundInstance extends AbstractTickableSoundInstance {
    private static final double RANGE_SQUARED = 30D * 30D;
    private final FensuBlockEntity fensu;

    public FensuSoundInstance(FensuBlockEntity fensu) {
        super(ModSounds.FENSU_HUM.get(), SoundSource.BLOCKS, RandomSource.create());
        this.fensu = fensu;
        looping = true;
        delay = 0;
        attenuation = Attenuation.LINEAR;
        x = fensu.getBlockPos().getX() + 0.5D;
        y = fensu.getBlockPos().getY() + 5.5D;
        z = fensu.getBlockPos().getZ() + 0.5D;
        updateMix();
    }

    @Override
    public void tick() {
        if (fensu.isRemoved() || fensu.speed() <= 0F || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) >= RANGE_SQUARED) {
            stop();
            return;
        }
        updateMix();
    }

    private void updateMix() {
        float speed = fensu.speed();
        volume = 1.5F;
        pitch = 0.5F + speed / 15F * 1.5F;
    }
}
