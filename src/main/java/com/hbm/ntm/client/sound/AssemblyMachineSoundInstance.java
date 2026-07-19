package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.AssemblyMachineBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class AssemblyMachineSoundInstance extends AbstractTickableSoundInstance {
    private final AssemblyMachineBlockEntity machine;

    public AssemblyMachineSoundInstance(AssemblyMachineBlockEntity machine) {
        super(ModSounds.MOTOR.get(), SoundSource.BLOCKS, RandomSource.create());
        this.machine = machine;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.5F;
        this.pitch = 0.75F;
        this.attenuation = Attenuation.LINEAR;
        this.x = machine.getBlockPos().getX() + 0.5D;
        this.y = machine.getBlockPos().getY() + 0.5D;
        this.z = machine.getBlockPos().getZ() + 0.5D;
    }

    @Override
    public void tick() {
        if (machine.isRemoved() || !machine.active() || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.distanceToSqr(x, y, z) >= 50D * 50D) stop();
    }
}
