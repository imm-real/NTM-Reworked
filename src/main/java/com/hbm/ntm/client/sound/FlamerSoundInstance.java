package com.hbm.ntm.client.sound;

import com.hbm.ntm.item.FlamerGunItem;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/** Client-side flame loop kept alive by a steady diet of cycle packets. */
public final class FlamerSoundInstance extends AbstractTickableSoundInstance {
    private static final Map<Integer, FlamerSoundInstance> SOUNDS = new HashMap<>();
    private final LivingEntity shooter;
    private int grace = 5;

    public static void keepAlive(LivingEntity shooter) {
        SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        FlamerSoundInstance sound = SOUNDS.get(shooter.getId());
        if (sound == null) {
            sound = new FlamerSoundInstance(shooter);
            SOUNDS.put(shooter.getId(), sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
        sound.grace = 5;
    }

    private FlamerSoundInstance(LivingEntity shooter) {
        super(ModSounds.GUN_FLAMER_LOOP.get(), SoundSource.PLAYERS, RandomSource.create());
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
                || !(shooter.getMainHandItem().getItem() instanceof FlamerGunItem gun)
                || gun.variant() == FlamerGunItem.Variant.DAYBREAKER) {
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
