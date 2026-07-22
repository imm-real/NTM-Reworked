package com.hbm.ntm.client.sound;

import com.hbm.ntm.item.FireExtinguisherItem;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/** Keeps the source 0.4 second extinguisher report continuous during automatic fire. */
public final class FireExtinguisherSoundInstance extends AbstractTickableSoundInstance {
    private static final Map<Integer, FireExtinguisherSoundInstance> SOUNDS = new HashMap<>();
    private final LivingEntity shooter;
    private int grace = 5;

    public static void keepAlive(LivingEntity shooter) {
        SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        FireExtinguisherSoundInstance sound = SOUNDS.get(shooter.getId());
        if (sound == null) {
            sound = new FireExtinguisherSoundInstance(shooter);
            SOUNDS.put(shooter.getId(), sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
        sound.grace = 5;
    }

    private FireExtinguisherSoundInstance(LivingEntity shooter) {
        super(ModSounds.GUN_EXTINGUISHER_FIRE.get(), SoundSource.PLAYERS, RandomSource.create());
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
                || !(shooter.getMainHandItem().getItem() instanceof FireExtinguisherItem)) {
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
