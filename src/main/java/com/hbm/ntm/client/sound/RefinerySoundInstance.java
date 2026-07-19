package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.RefineryBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Source boiler loop: 0.25 volume, 15-block range and a 20-tick activity tail. */
public final class RefinerySoundInstance extends AbstractTickableSoundInstance {
    private static final int ACTIVITY_TAIL = 20;
    private static final double RANGE_SQUARED = 15D * 15D;

    private final RefineryBlockEntity refinery;
    private int activityTail = ACTIVITY_TAIL;

    public RefinerySoundInstance(RefineryBlockEntity refinery) {
        super(ModSounds.BOILER.get(), SoundSource.BLOCKS, RandomSource.create());
        this.refinery = refinery;
        looping = true;
        delay = 0;
        volume = 0.25F;
        pitch = 1F;
        attenuation = Attenuation.LINEAR;
        x = refinery.getBlockPos().getX() + 0.5D;
        y = refinery.getBlockPos().getY() + 0.5D;
        z = refinery.getBlockPos().getZ() + 0.5D;
    }

    @Override public void tick() {
        if (refinery.active()) activityTail = ACTIVITY_TAIL;
        else activityTail--;
        if (refinery.isRemoved() || activityTail <= 0 || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) >= RANGE_SQUARED) stop();
    }
}
