package com.hbm.ntm.conveyor;

import com.hbm.ntm.entity.MovingConveyorItemEntity;
import com.hbm.ntm.entity.MovingConveyorPackageEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Endpoint that accepts objects from a conveyor. */
public interface ConveyorEnterable {
    default boolean canConveyorItemEnter(Level level, BlockPos pos, Direction side,
                                         MovingConveyorItemEntity item) {
        return false;
    }

    default void onConveyorItemEnter(Level level, BlockPos pos, Direction side,
                                     MovingConveyorItemEntity item) {
    }

    default boolean canConveyorPackageEnter(Level level, BlockPos pos, Direction side,
                                            MovingConveyorPackageEntity conveyorPackage) {
        return false;
    }

    default void onConveyorPackageEnter(Level level, BlockPos pos, Direction side,
                                        MovingConveyorPackageEntity conveyorPackage) {
    }
}
