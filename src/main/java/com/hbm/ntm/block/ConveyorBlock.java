package com.hbm.ntm.block;

import com.hbm.ntm.conveyor.ConveyorCurve;
import com.hbm.ntm.conveyor.ConveyorType;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Flat, bendable conveyor used by all four speed/lane variants. */
public final class ConveyorBlock extends AbstractConveyorBlock {
    public static final EnumProperty<ConveyorCurve> CURVE = EnumProperty.create("curve", ConveyorCurve.class);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 4, 16);
    private final ConveyorType type;

    public ConveyorBlock(Properties properties, ConveyorType type) {
        super(properties, type);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(CURVE, ConveyorCurve.STRAIGHT));
    }

    public ConveyorType type() {
        return type;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(CURVE, ConveyorCurve.STRAIGHT);
    }

    @Override
    public Direction inputDirection(Level level, BlockPos pos, BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public Direction outputDirection(Level level, BlockPos pos, BlockState state) {
        Direction straight = state.getValue(FACING).getOpposite();
        return switch (state.getValue(CURVE)) {
            case STRAIGHT -> straight;
            case LEFT -> straight.getCounterClockWise();
            case RIGHT -> straight.getClockWise();
        };
    }

    @Override
    public Direction travelDirection(Level level, BlockPos pos, BlockState state, Vec3 itemPosition) {
        Direction input = inputDirection(level, pos, state);
        ConveyorCurve curve = state.getValue(CURVE);
        if (curve == ConveyorCurve.STRAIGHT) {
            return input;
        }

        // Bend geometry pivots around one
        // corner and changes direction after crossing its Manhattan-radius-one
        // diamond. This matters for double/triple belts: every lane reaches the
        // turn at a different point instead of all lanes snapping at block center.
        Direction clockwise = input.getClockWise();
        double side = curve == ConveyorCurve.LEFT ? 0.5D : -0.5D;
        double pivotX = pos.getX() + 0.5D + input.getStepX() * 0.5D
                - clockwise.getStepX() * side;
        double pivotZ = pos.getZ() + 0.5D + input.getStepZ() * 0.5D
                - clockwise.getStepZ() * side;
        if (Math.abs(itemPosition.x - pivotX) + Math.abs(itemPosition.z - pivotZ) >= 1.0D) {
            return curve == ConveyorCurve.LEFT ? clockwise.getOpposite() : clockwise;
        }
        return input;
    }

    @Override
    public Vec3 closestSnappingPosition(Level level, BlockPos pos, BlockState state, Vec3 itemPosition) {
        Direction direction = travelDirection(level, pos, state, itemPosition);
        double x = Mth.clamp(itemPosition.x, pos.getX(), pos.getX() + 1.0D);
        double z = Mth.clamp(itemPosition.z, pos.getZ(), pos.getZ() + 1.0D);
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;

        if (direction.getAxis() == Direction.Axis.X) {
            z = laneCoordinate(z, centerZ);
        } else {
            x = laneCoordinate(x, centerX);
        }
        return new Vec3(x, pos.getY() + 0.25D, z);
    }

    private double laneCoordinate(double coordinate, double center) {
        return switch (type.lanes()) {
            case 2 -> center + (coordinate > center ? 0.25D : -0.25D);
            case 3 -> center + (coordinate > center + 0.15D ? 0.3125D
                    : coordinate < center - 0.15D ? -0.3125D : 0.0D);
            default -> center;
        };
    }

    @Override
    public double speedMultiplier(Level level, BlockPos pos, BlockState state, Vec3 itemPosition) {
        return type.speedMultiplier();
    }

    @Override
    protected void onScrewdriver(Level level, BlockPos pos, BlockState state, boolean sneaking) {
        if (!sneaking) {
            level.setBlock(pos, state.setValue(FACING, rotate(state.getValue(FACING))), UPDATE_ALL);
            return;
        }
        ConveyorCurve curve = state.getValue(CURVE);
        if (type == ConveyorType.REGULAR && curve == ConveyorCurve.RIGHT) {
            level.setBlock(pos, ModBlocks.CONVEYOR_LIFT.get().defaultBlockState()
                    .setValue(FACING, state.getValue(FACING)), UPDATE_ALL);
        } else {
            level.setBlock(pos, state.setValue(CURVE, curve.next()), UPDATE_ALL);
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
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,
            BlockState> builder) {
        builder.add(FACING, CURVE);
    }
}
