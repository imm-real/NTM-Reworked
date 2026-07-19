package com.hbm.ntm.blockentity;

import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sends one chlorine cloud stumbling through 51 cells per powered tick. */
public final class ChlorineSealBlockEntity extends BlockEntity {
    static final int MAX_SPREAD_INDEX = 50;
    private static final Direction[] SOURCE_DIRECTION_ORDER = {
            Direction.EAST, Direction.WEST, Direction.UP,
            Direction.DOWN, Direction.SOUTH, Direction.NORTH
    };

    public ChlorineSealBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VENT_CHLORINE_SEAL.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChlorineSealBlockEntity seal) {
        if (level instanceof ServerLevel server && server.hasNeighborSignal(pos)) {
            seal.spread(server, pos, 0);
        }
    }

    private void spread(ServerLevel level, BlockPos pos, int index) {
        if (index > MAX_SPREAD_INDEX) return;

        BlockState state = level.getBlockState(pos);
        if (state.canBeReplaced()) {
            level.setBlock(pos, ModBlocks.CHLORINE_GAS.get().defaultBlockState(), Block.UPDATE_ALL);
            state = level.getBlockState(pos);
        }

        if (!state.is(ModBlocks.CHLORINE_GAS.get()) && !state.is(ModBlocks.VENT_CHLORINE_SEAL.get())) return;

        Direction direction = sourceDirection(level.random.nextInt(SOURCE_DIRECTION_ORDER.length));
        spread(level, pos.relative(direction), index + 1);
    }

    static Direction sourceDirection(int roll) {
        return SOURCE_DIRECTION_ORDER[roll];
    }
}
