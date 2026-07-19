package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.ChemicalPlantBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class ChemicalPlantSoundInstance extends AbstractTickableSoundInstance {
    private final ChemicalPlantBlockEntity plant;
    public ChemicalPlantSoundInstance(ChemicalPlantBlockEntity plant) {
        super(ModSounds.CHEMPLANT_OPERATE.get(), SoundSource.BLOCKS, RandomSource.create());
        this.plant = plant;
        looping = true; delay = 0; volume = 1F; pitch = 1F; attenuation = Attenuation.LINEAR;
        x = plant.getBlockPos().getX() + 0.5D;
        y = plant.getBlockPos().getY() + 0.5D;
        z = plant.getBlockPos().getZ() + 0.5D;
    }
    @Override public void tick() {
        if (plant.isRemoved() || !plant.active() || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) >= 30D * 30D) stop();
    }
}
