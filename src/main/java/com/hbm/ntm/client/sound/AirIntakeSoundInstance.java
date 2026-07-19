package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.AirIntakeBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Source electric-motor loop: volume 0.25, pitch 1 and ten-block range. */
public final class AirIntakeSoundInstance extends AbstractTickableSoundInstance {
    private final AirIntakeBlockEntity intake;

    public AirIntakeSoundInstance(AirIntakeBlockEntity intake) {
        super(ModSounds.MOTOR.get(), SoundSource.BLOCKS, RandomSource.create());
        this.intake = intake;
        looping = true;
        delay = 0;
        volume = 0.25F;
        pitch = 1.0F;
        attenuation = Attenuation.LINEAR;
        x = intake.getBlockPos().getX() + 0.5D;
        y = intake.getBlockPos().getY() + 0.5D;
        z = intake.getBlockPos().getZ() + 0.5D;
    }

    @Override public void tick() {
        if (intake.isRemoved() || !intake.active() || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) >= 100.0D) stop();
    }
}
