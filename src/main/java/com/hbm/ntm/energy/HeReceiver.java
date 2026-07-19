package com.hbm.ntm.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface HeReceiver extends HeHandler {
    default long transferPower(long power) {
        if (power + getPower() <= getMaxPower()) {
            setPower(power + getPower());
            return 0;
        }
        long capacity = getMaxPower() - getPower();
        long overshoot = power - capacity;
        setPower(getMaxPower());
        return overshoot;
    }

    default long getReceiverSpeed() {
        return getMaxPower();
    }

    default boolean allowDirectProvision() {
        return true;
    }

    default void trySubscribe(ServerLevel level, BlockPos target, Direction direction) {
        if (!level.hasChunkAt(target)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (blockEntity instanceof HeConductor conductor && conductor.canConnect(direction.getOpposite())) {
            HeNode node = HeNetworkManager.get(level).getNode(target);
            if (node != null && node.hasValidNetwork()) {
                node.network().addReceiver(this);
            }
        }
    }

    default void tryUnsubscribe(ServerLevel level, BlockPos target) {
        if (!level.hasChunkAt(target)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (blockEntity instanceof HeConductor conductor) {
            // Preserved 1.7.10 quirk: this checks a fresh, unattached node and therefore
            // does not normally remove the receiver from the live network.
            HeNode node = conductor.createNode(target);
            if (node.network() != null) {
                node.network().removeReceiver(this);
            }
        }
    }

    default ConnectionPriority getPriority() {
        return ConnectionPriority.NORMAL;
    }

    enum ConnectionPriority {
        LOWEST,
        LOW,
        NORMAL,
        HIGH,
        HIGHEST
    }
}
