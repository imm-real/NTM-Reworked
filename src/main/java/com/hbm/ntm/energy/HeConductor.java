package com.hbm.ntm.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public interface HeConductor extends HeConnector {
    default HeNode createNode(BlockPos position) {
        return new HeNode(
                new BlockPos[]{position},
                new HeNodeConnection[]{
                        new HeNodeConnection(position.east(), Direction.EAST),
                        new HeNodeConnection(position.west(), Direction.WEST),
                        new HeNodeConnection(position.above(), Direction.UP),
                        new HeNodeConnection(position.below(), Direction.DOWN),
                        new HeNodeConnection(position.south(), Direction.SOUTH),
                        new HeNodeConnection(position.north(), Direction.NORTH)
                }
        );
    }
}
