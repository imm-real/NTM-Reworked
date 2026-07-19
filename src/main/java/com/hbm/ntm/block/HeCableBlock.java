package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.HeCableBlockEntity;
import com.hbm.ntm.energy.HeConnector;
import com.hbm.ntm.energy.HeNetworkManager;
import com.hbm.ntm.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class HeCableBlock extends BaseEntityBlock {
    public static final MapCodec<HeCableBlock> CODEC = simpleCodec(HeCableBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final EnumProperty<CableRenderShape> RENDER_SHAPE =
            EnumProperty.create("render_shape", CableRenderShape.class);

    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = new EnumMap<>(Direction.class);

    static {
        PROPERTY_BY_DIRECTION.put(Direction.NORTH, NORTH);
        PROPERTY_BY_DIRECTION.put(Direction.EAST, EAST);
        PROPERTY_BY_DIRECTION.put(Direction.SOUTH, SOUTH);
        PROPERTY_BY_DIRECTION.put(Direction.WEST, WEST);
        PROPERTY_BY_DIRECTION.put(Direction.UP, UP);
        PROPERTY_BY_DIRECTION.put(Direction.DOWN, DOWN);
    }

    public HeCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(RENDER_SHAPE, CableRenderShape.JUNCTION));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(context.getLevel(), context.getClickedPos(), direction));
        }
        return updateRenderShape(state);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos position,
            BlockPos neighborPosition
    ) {
        return updateRenderShape(
                state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(level, position, direction))
        );
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        double minX = state.getValue(WEST) ? 0D : 5.5D;
        double maxX = state.getValue(EAST) ? 16D : 10.5D;
        double minY = state.getValue(DOWN) ? 0D : 5.5D;
        double maxY = state.getValue(UP) ? 16D : 10.5D;
        double minZ = state.getValue(NORTH) ? 0D : 5.5D;
        double maxZ = state.getValue(SOUTH) ? 16D : 10.5D;
        return box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos position) {
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            HeNetworkManager.get(serverLevel).destroyNode(position);
        }
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new HeCableBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? null
                : createTickerHelper(type, ModBlockEntities.RED_CABLE.get(), HeCableBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, RENDER_SHAPE);
    }

    public BlockState updateRenderShape(BlockState state) {
        boolean north = state.getValue(NORTH);
        boolean east = state.getValue(EAST);
        boolean south = state.getValue(SOUTH);
        boolean west = state.getValue(WEST);
        boolean up = state.getValue(UP);
        boolean down = state.getValue(DOWN);

        CableRenderShape shape = CableRenderShape.JUNCTION;
        if (east && west && !north && !south && !up && !down) {
            shape = CableRenderShape.X;
        } else if (up && down && !north && !south && !east && !west) {
            shape = CableRenderShape.Y;
        } else if (north && south && !east && !west && !up && !down) {
            shape = CableRenderShape.Z;
        }
        return state.setValue(RENDER_SHAPE, shape);
    }

    private boolean connectsTo(BlockGetter level, BlockPos position, Direction direction) {
        BlockPos neighborPosition = position.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPosition);
        if (neighborState.getBlock() instanceof HeCableBlock) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(neighborPosition);
        return blockEntity instanceof HeConnector connector && connector.canConnect(direction.getOpposite());
    }

    public enum CableRenderShape implements StringRepresentable {
        JUNCTION("junction"),
        X("x"),
        Y("y"),
        Z("z");

        private final String serializedName;

        CableRenderShape(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
