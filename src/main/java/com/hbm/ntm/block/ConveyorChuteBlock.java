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

/** Conveyor that has discovered gravity, with an exit at the bottom. */
public final class ConveyorChuteBlock extends AbstractConveyorBlock {
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public ConveyorChuteBlock(Properties properties) {
        super(properties, ConveyorType.REGULAR);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(BOTTOM, true)
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return recalculateState(super.getStateForPlacement(context), context.getLevel(),
                context.getClickedPos());
    }

    /** Neighbors open grates; the block below decides down or sideways. */
    public BlockState recalculateState(BlockState state, LevelAccessor level, BlockPos pos) {
        boolean bottom = !isConveyor(level, pos.below())
                && !isEntryTarget(level, pos.below(), Direction.UP);
        return state.setValue(BOTTOM, bottom)
                .setValue(NORTH, isConveyor(level, pos.north()))
                .setValue(EAST, isConveyor(level, pos.east()))
                .setValue(SOUTH, isConveyor(level, pos.south()))
                .setValue(WEST, isConveyor(level, pos.west()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN || direction.getAxis().isHorizontal()
                ? recalculateState(state, level, pos) : state;
    }

    @Override
    public Direction inputDirection(Level level, BlockPos pos, BlockState state) {
        return Direction.UP;
    }

    @Override
    public Direction outputDirection(Level level, BlockPos pos, BlockState state) {
        return Direction.DOWN;
    }

    private boolean descending(BlockState state, BlockPos pos, Vec3 itemPosition) {
        return !state.getValue(BOTTOM) || itemPosition.y > pos.getY() + 0.25D;
    }

    @Override
    public Direction travelDirection(Level level, BlockPos pos, BlockState state, Vec3 itemPosition) {
        return descending(state, pos, itemPosition) ? Direction.UP : state.getValue(FACING);
    }

    @Override
    public Vec3 closestSnappingPosition(Level level, BlockPos pos, BlockState state, Vec3 itemPosition) {
        if (descending(state, pos, itemPosition)) {
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
    public double speedMultiplier(Level level, BlockPos pos, BlockState state, Vec3 itemPosition) {
        if (!state.getValue(BOTTOM)) {
            return 5.0D;
        }
        return itemPosition.y > pos.getY() + 0.25D ? 3.0D : 1.0D;
    }

    @Override
    protected void onScrewdriver(Level level, BlockPos pos, BlockState state, boolean sneaking) {
        if (sneaking) {
            level.setBlock(pos, ModBlocks.CONVEYOR.get().defaultBlockState()
                    .setValue(FACING, state.getValue(FACING)), UPDATE_ALL);
        } else {
            level.setBlock(pos, state.setValue(FACING, rotate(state.getValue(FACING))), UPDATE_ALL);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BOTTOM, NORTH, EAST, SOUTH, WEST);
    }
}
