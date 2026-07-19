package com.hbm.ntm.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface HeProvider extends HeHandler {
    default void usePower(long power) {
        setPower(getPower() - power);
    }

    default long getProviderSpeed() {
        return getMaxPower();
    }

    default void tryProvide(ServerLevel level, BlockPos target, Direction direction) {
        if (!level.hasChunkAt(target)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(target);
        HeNetworkManager manager = HeNetworkManager.get(level);

        if (blockEntity instanceof HeConductor conductor && conductor.canConnect(direction.getOpposite())) {
            HeNode node = manager.getNode(target);
            if (node != null && node.hasValidNetwork()) {
                node.network().addProvider(this);
            }
        }

        if (blockEntity instanceof HeReceiver receiver
                && blockEntity != this
                && receiver.canConnect(direction.getOpposite())
                && receiver.allowDirectProvision()) {
            long provides = Math.min(getPower(), getProviderSpeed());
            long receives = Math.min(receiver.getMaxPower() - receiver.getPower(), receiver.getReceiverSpeed());
            long toTransfer = Math.min(provides, receives);
            toTransfer -= receiver.transferPower(toTransfer);
            usePower(toTransfer);
        }
    }
}
