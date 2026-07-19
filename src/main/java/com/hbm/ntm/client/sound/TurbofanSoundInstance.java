package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.TurbofanBlockEntity;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.Map;
import java.util.WeakHashMap;

/** Fifty-block turbofan racket mixed by momentum and afterburner. */
public final class TurbofanSoundInstance extends AbstractTickableSoundInstance {
    private static final Map<TurbofanBlockEntity, TurbofanSoundInstance> SOUNDS = new WeakHashMap<>();
    private final TurbofanBlockEntity turbofan;

    public static void tick(TurbofanBlockEntity turbofan) {
        SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (turbofan.momentum() <= 0 || SOUNDS.containsKey(turbofan)) return;
        TurbofanSoundInstance sound = new TurbofanSoundInstance(turbofan);
        SOUNDS.put(turbofan, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    public TurbofanSoundInstance(TurbofanBlockEntity turbofan) {
        super(ModSounds.TURBOFAN_OPERATE.get(), SoundSource.BLOCKS, RandomSource.create());
        this.turbofan = turbofan;
        looping = true;
        delay = 0;
        attenuation = Attenuation.LINEAR;
        // TileEntityMachineTurbofan created its AudioDynamic at the integer core coordinates.
        x = turbofan.getBlockPos().getX();
        y = turbofan.getBlockPos().getY();
        z = turbofan.getBlockPos().getZ();
        updateMix();
    }

    @Override
    public void tick() {
        if (turbofan.isRemoved() || turbofan.momentum() <= 0) {
            stop();
            return;
        }
        updateMix();
    }

    private void updateMix() {
        volume = turbofan.momentum() / 50.0F;
        pitch = turbofan.momentum() / 200.0F + 0.5F + turbofan.afterburner() * 0.16F;
    }
}
