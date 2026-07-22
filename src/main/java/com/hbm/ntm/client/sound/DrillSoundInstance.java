package com.hbm.ntm.client.sound;

import com.hbm.ntm.item.DrillItem;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

public final class DrillSoundInstance extends AbstractTickableSoundInstance {
    private static final Map<Integer, DrillSoundInstance> SOUNDS = new HashMap<>();
    private final LivingEntity shooter;
    private int grace = 25;

    public static void keepAlive(LivingEntity shooter) {
        SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        DrillSoundInstance sound = SOUNDS.get(shooter.getId());
        if (sound == null) {
            sound = new DrillSoundInstance(shooter);
            SOUNDS.put(shooter.getId(), sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
        sound.grace = 25;
    }

    private DrillSoundInstance(LivingEntity shooter) {
        super(ModSounds.ENGINE.get(), SoundSource.PLAYERS, RandomSource.create());
        this.shooter = shooter;
        looping = true;
        delay = 0;
        volume = 1.0F;
        pitch = 1.0F;
        attenuation = Attenuation.LINEAR;
        updatePosition();
    }

    @Override
    public void tick() {
        if (!shooter.isAlive() || --grace < 0
                || !(shooter.getMainHandItem().getItem() instanceof DrillItem)) {
            stop();
            return;
        }
        updatePosition();
    }

    private void updatePosition() {
        x = shooter.getX();
        y = shooter.getY() + shooter.getBbHeight() * 0.5D;
        z = shooter.getZ();
    }
}
