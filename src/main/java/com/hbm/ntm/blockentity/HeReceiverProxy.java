package com.hbm.ntm.blockentity;

import com.hbm.ntm.energy.HeReceiver;
import org.jetbrains.annotations.Nullable;

interface HeReceiverProxy extends HeReceiver {
    @Nullable
    HeReceiver target();

    @Override
    default long getPower() {
        HeReceiver target = target();
        return target == null ? 0L : target.getPower();
    }

    @Override
    default void setPower(long power) {
        HeReceiver target = target();
        if (target != null) {
            target.setPower(power);
        }
    }

    @Override
    default long getMaxPower() {
        HeReceiver target = target();
        return target == null ? 0L : target.getMaxPower();
    }

    @Override
    default long transferPower(long power) {
        HeReceiver target = target();
        return target == null ? power : target.transferPower(power);
    }

    @Override
    default long getReceiverSpeed() {
        HeReceiver target = target();
        return target == null ? 0L : target.getReceiverSpeed();
    }

    @Override
    default boolean allowDirectProvision() {
        HeReceiver target = target();
        return target != null && target.allowDirectProvision();
    }

    @Override
    default ConnectionPriority getPriority() {
        HeReceiver target = target();
        return target == null ? ConnectionPriority.NORMAL : target.getPriority();
    }

    @Override
    default boolean isHeLoaded() {
        HeReceiver target = target();
        return target != null && target.isHeLoaded();
    }
}
