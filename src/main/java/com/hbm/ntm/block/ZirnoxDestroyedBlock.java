package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.ZirnoxDestroyedBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/** Two-layer radioactive wreck left by a ZIRNOX meltdown. */
public final class ZirnoxDestroyedBlock extends BaseEntityBlock {
    public static final MapCodec<ZirnoxDestroyedBlock> CODEC = simpleCodec(ZirnoxDestroyedBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 49);
    private static final int CORE = index(0, 0, 0);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    public ZirnoxDestroyedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, CORE));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    public static void place(Level level, BlockPos core, Direction facing, ZirnoxDestroyedBlock block) {
        for (int y = 0; y < 2; y++) for (int forward = -2; forward <= 2; forward++)
            for (int side = -2; side <= 2; side++) {
                BlockPos pos = core.relative(facing.getClockWise(), side).relative(facing.getOpposite(), forward).above(y);
                level.setBlock(pos, block.defaultBlockState().setValue(FACING, facing)
                        .setValue(PART, index(side, y, forward)), Block.UPDATE_ALL);
            }
    }

    public static BlockPos corePosition(BlockPos pos, BlockState state) {
        int part = state.getValue(PART), y = part / 25, plane = part % 25;
        int forward = plane / 5 - 2, side = plane % 5 - 2;
        Direction facing = state.getValue(FACING);
        return pos.relative(facing.getClockWise(), -side).relative(facing.getOpposite(), -forward).below(y);
    }
    private static int index(int side, int y, int forward) { return y * 25 + (forward + 2) * 5 + side + 2; }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !REMOVING.get()) {
            REMOVING.set(true);
            try {
                BlockPos core = corePosition(pos, state); Direction facing = state.getValue(FACING);
                for (int y = 0; y < 2; y++) for (int forward = -2; forward <= 2; forward++)
                    for (int side = -2; side <= 2; side++) {
                        BlockPos part = core.relative(facing.getClockWise(), side)
                                .relative(facing.getOpposite(), forward).above(y);
                        if (!part.equals(pos) && level.getBlockState(part).is(this)) level.setBlock(part,
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
            } finally { REMOVING.set(false); }
        }
        super.onRemove(state, level, pos, next, moved);
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == CORE ? new ZirnoxDestroyedBlockEntity(pos, state) : null;
    }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                      BlockEntityType<T> type) {
        return state.getValue(PART) == CORE ? createTickerHelper(type, ModBlockEntities.ZIRNOX_DESTROYED.get(),
                ZirnoxDestroyedBlockEntity::tick) : null;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
}
