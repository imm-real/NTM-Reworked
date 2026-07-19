package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.CentrifugeBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Source centrifuge audio accumulator: +2 active, -3 idle, clamped to 0..60. */
public final class CentrifugeSoundInstance extends AbstractTickableSoundInstance {
    private final CentrifugeBlockEntity machine;
    private int audioDuration;

    public CentrifugeSoundInstance(CentrifugeBlockEntity machine) {
        super(ModSounds.CENTRIFUGE_OPERATE.get(), SoundSource.BLOCKS, RandomSource.create());
        this.machine = machine;
        looping = true;
        delay = 0;
        volume = 0F;
        pitch = 0.5F;
        attenuation = Attenuation.LINEAR;
        x = machine.getBlockPos().getX() + 0.5D;
        y = machine.getBlockPos().getY() + 0.5D;
        z = machine.getBlockPos().getZ() + 0.5D;
    }

    @Override public void tick() {
        if (machine.isRemoved() || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) >= 25D * 25D) {
            stop();
            return;
        }
        audioDuration = Mth.clamp(audioDuration + (machine.active() ? 2 : -3), 0, 60);
        if (!machine.active() && audioDuration <= 10) {
            stop();
            return;
        }
        volume = audioDuration > 10 ? 1F : 0F;
        pitch = (audioDuration - 10) / 100F + 0.5F;
    }
}
