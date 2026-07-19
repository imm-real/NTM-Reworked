package com.hbm.ntm.blockentity;

import com.hbm.ntm.energy.HeProvider;
import org.jetbrains.annotations.Nullable;

interface HeProviderProxy extends HeProvider {
    @Nullable
    HeProvider target();

    @Override
    default long getPower() {
        HeProvider target = target();
        return target == null ? 0L : target.getPower();
    }

    @Override
    default void setPower(long power) {
        HeProvider target = target();
        if (target != null) {
            target.setPower(power);
        }
    }

    @Override
    default long getMaxPower() {
        HeProvider target = target();
        return target == null ? 0L : target.getMaxPower();
    }

    @Override
    default void usePower(long power) {
        HeProvider target = target();
        if (target != null) {
            target.usePower(power);
        }
    }

    @Override
    default long getProviderSpeed() {
        HeProvider target = target();
        return target == null ? 0L : target.getProviderSpeed();
    }

    @Override
    default boolean isHeLoaded() {
        HeProvider target = target();
        return target != null && target.isHeLoaded();
    }
}
