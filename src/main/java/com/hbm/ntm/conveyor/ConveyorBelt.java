package com.hbm.ntm.conveyor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Movement contract shared by flat belts, chain lifts and chutes. */
public interface ConveyorBelt {
    Direction inputDirection(Level level, BlockPos pos, BlockState state);

    Direction outputDirection(Level level, BlockPos pos, BlockState state);

    Direction travelDirection(Level level, BlockPos pos, BlockState state, Vec3 itemPosition);

    Vec3 closestSnappingPosition(Level level, BlockPos pos, BlockState state, Vec3 itemPosition);

    default boolean canItemStay(Level level, BlockPos pos, BlockState state, Vec3 itemPosition) {
        return true;
    }

    default double speedMultiplier(Level level, BlockPos pos, BlockState state, Vec3 itemPosition) {
        return 1.0D;
    }

    default Vec3 travelLocation(Level level, BlockPos pos, BlockState state, Vec3 itemPosition,
                                double baseSpeed) {
        Direction direction = travelDirection(level, pos, state, itemPosition);
        Vec3 snap = closestSnappingPosition(level, pos, state, itemPosition);
        double speed = baseSpeed * speedMultiplier(level, pos, state, itemPosition);
        Vec3 destination = snap.subtract(direction.getStepX() * speed,
                direction.getStepY() * speed, direction.getStepZ() * speed);
        Vec3 delta = destination.subtract(itemPosition);
        double length = delta.length();
        if (length < 1.0E-7D) {
            return itemPosition;
        }
        return itemPosition.add(delta.scale(Math.min(speed, length) / length));
    }
}
