package com.hbm.ntm.block;

import com.hbm.ntm.radiation.RadiationSystem;
import net.minecraft.world.entity.LivingEntity;

/** Turns old radiation amplifier levels into contamination. */
final class LegacyRadiationBlockEffects {
    private static final int DURATION = 30 * 20;

    private LegacyRadiationBlockEffects() {
    }

    static void refresh(LivingEntity entity, int amplifier) {
        float radiationPerTick = (amplifier + 1) * 0.05F;
        var data = RadiationSystem.data(entity);
        data.contamination().removeIf(effect -> effect.maxTime() == DURATION
                && !effect.bypassResistance()
                && Math.abs(effect.maxRadiation() - radiationPerTick) < 0.00001F);
        data.addContamination(radiationPerTick, DURATION, false);
    }
}
