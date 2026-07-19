package com.hbm.ntm.block;

import com.hbm.ntm.conveyor.ConveyorType;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Conveyor lift with a bottom, a top and several opinions in between. */
public final class ConveyorLiftBlock extends AbstractConveyorBlock {
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    private static final VoxelShape FULL_SHAPE = box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape TOP_SHAPE = box(0, 0, 0, 16, 8, 16);

    public ConveyorLiftBlock(Properties properties) {
        super(properties, ConveyorType.REGULAR);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(BOTTOM, true).setValue(TOP, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return recalculate(super.getStateForPlacement(context), context.getLevel(), context.getClickedPos());
    }

    private BlockState recalculate(BlockState state, LevelAccessor level, BlockPos pos) {
        boolean bottom = !isConveyor(level, pos.below());
        boolean top = !bottom && !isConveyor(level, pos.above())
                && !isEntryTarget(level, pos.above(), Direction.DOWN);
        return state.setValue(BOTTOM, bottom).setValue(TOP, top);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction.getAxis() == Direction.Axis.Y ? recalculate(state, level, pos) : state;
    }

    @Override
    public Direction inputDirection(Level level, BlockPos pos, BlockState state) {
        return Direction.DOWN;
    }

    @Override
    public Direction outputDirection(Level level, BlockPos pos, BlockState state) {
        return Direction.UP;
    }

    @Override
    public Direction travelDirection(Level level, BlockPos pos, BlockState state, Vec3 itemPosition) {
        return state.getValue(TOP) ? state.getValue(FACING) : Direction.DOWN;
    }

    @Override
    public Vec3 closestSnappingPosition(Level level, BlockPos pos, BlockState state, Vec3 itemPosition) {
        if (!state.getValue(TOP)) {
            return new Vec3(pos.getX() + 0.5D, itemPosition.y, pos.getZ() + 0.5D);
        }
        Direction direction = state.getValue(FACING);
        double x = direction.getAxis() == Direction.Axis.X
                ? Mth.clamp(itemPosition.x, pos.getX(), pos.getX() + 1.0D) : pos.getX() + 0.5D;
        double z = direction.getAxis() == Direction.Axis.Z
                ? Mth.clamp(itemPosition.z, pos.getZ(), pos.getZ() + 1.0D) : pos.getZ() + 0.5D;
        return new Vec3(x, pos.getY() + 0.25D, z);
    }

    @Override
    protected void onScrewdriver(Level level, BlockPos pos, BlockState state, boolean sneaking) {
        if (sneaking) {
            ConveyorChuteBlock chute = ModBlocks.CONVEYOR_CHUTE.get();
            BlockState replacement = chute.defaultBlockState()
                    .setValue(FACING, state.getValue(FACING));
            level.setBlock(pos, chute.recalculateState(replacement, level, pos), UPDATE_ALL);
        } else {
            level.setBlock(pos, state.setValue(FACING, rotate(state.getValue(FACING))), UPDATE_ALL);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TOP) ? TOP_SHAPE : FULL_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BOTTOM, TOP);
    }
}
