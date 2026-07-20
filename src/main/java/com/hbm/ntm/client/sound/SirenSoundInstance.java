package com.hbm.ntm.client.sound;

import com.hbm.ntm.blockentity.SirenBlockEntity;
import com.hbm.ntm.item.SirenTrackItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Map;
import java.util.WeakHashMap;

/** The source siren had no attenuation, just arithmetic and confidence. */
public final class SirenSoundInstance extends AbstractTickableSoundInstance {
    private static final Map<SirenBlockEntity, SirenSoundInstance> LOOPS = new WeakHashMap<>();
    private static final Map<SirenBlockEntity, Integer> SEEN_PULSES = new WeakHashMap<>();

    private final SirenBlockEntity siren;
    private final SirenTrackItem.Track track;

    public static void tick(SirenBlockEntity siren) {
        LOOPS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        SirenTrackItem.Track track = siren.soundTrack();
        SirenSoundInstance loop = LOOPS.get(siren);

        if (track.type() == SirenTrackItem.SoundType.LOOP && track != SirenTrackItem.Track.NONE) {
            if (loop != null && loop.track != track) {
                loop.stop();
                LOOPS.remove(siren);
                loop = null;
            }
            if (siren.active() && loop == null) {
                loop = new SirenSoundInstance(siren, track, true);
                LOOPS.put(siren, loop);
                Minecraft.getInstance().getSoundManager().play(loop);
            } else if (!siren.active() && loop != null) {
                loop.stop();
                LOOPS.remove(siren);
            }
        } else if (loop != null) {
            loop.stop();
            LOOPS.remove(siren);
        }

        Integer seen = SEEN_PULSES.put(siren, siren.pulseSerial());
        if ((seen == null ? siren.pulseSerial() > 0 : seen != siren.pulseSerial())
                && track != SirenTrackItem.Track.NONE
                && track.type() != SirenTrackItem.SoundType.LOOP) {
            Minecraft.getInstance().getSoundManager().play(new SirenSoundInstance(siren, track, false));
        }
    }

    private SirenSoundInstance(SirenBlockEntity siren, SirenTrackItem.Track track, boolean looping) {
        super(track.sound(), SoundSource.RECORDS, RandomSource.create());
        this.siren = siren;
        this.track = track;
        this.looping = looping;
        delay = 0;
        attenuation = Attenuation.NONE;
        x = siren.getBlockPos().getX();
        y = siren.getBlockPos().getY();
        z = siren.getBlockPos().getZ();
        pitch = 1.0F;
        updateVolume();
    }

    @Override public void tick() {
        if (siren.isRemoved() || looping && (!siren.active() || siren.soundTrack() != track)) {
            stop();
            return;
        }
        updateVolume();
    }

    private void updateVolume() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            volume = 2.0F;
            return;
        }
        double distance = Math.sqrt(player.distanceToSqr(x, y, z));
        volume = Mth.clamp((float) (2.0D - distance / track.range() * 2.0D), 0.0F, 2.0F);
    }
}
