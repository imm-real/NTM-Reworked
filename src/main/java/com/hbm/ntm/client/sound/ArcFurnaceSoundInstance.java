package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.ArcFurnaceBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Source processing hum: fixed 15-block range, 1.5 volume, and 0.75 pitch. */
public final class ArcFurnaceSoundInstance extends AbstractTickableSoundInstance {
    private static final double RANGE_SQUARED = 15D * 15D;
    private final ArcFurnaceBlockEntity furnace;

    public ArcFurnaceSoundInstance(ArcFurnaceBlockEntity furnace) {
        super(ModSounds.ELECTRIC_HUM.get(), SoundSource.BLOCKS, RandomSource.create());
        this.furnace = furnace;
        looping = true;
        delay = 0;
        volume = 1.5F;
        pitch = 0.75F;
        attenuation = Attenuation.LINEAR;
        x = furnace.getBlockPos().getX() + 0.5D;
        y = furnace.getBlockPos().getY() + 0.5D;
        z = furnace.getBlockPos().getZ() + 0.5D;
    }

    @Override public void tick() {
        if (furnace.isRemoved() || !furnace.progressing() || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) >= RANGE_SQUARED) stop();
    }
}
