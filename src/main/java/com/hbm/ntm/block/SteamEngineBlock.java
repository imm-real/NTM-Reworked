package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.SteamEngineBlockEntity;
import com.hbm.ntm.blockentity.SteamEngineProxyBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Seven by two by three blocks of Victorian optimism. */
public final class SteamEngineBlock extends BaseEntityBlock {
    public static final MapCodec<SteamEngineBlock> CODEC = simpleCodec(SteamEngineBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Longways, scrubbed clean of negative blockstate values. */
    public static final IntegerProperty PART_LENGTH = IntegerProperty.create("part_length", 0, 6);
    /** Sideways, also numerically sanitized. */
    public static final IntegerProperty PART_SIDE = IntegerProperty.create("part_side", 0, 2);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 1);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = box(0D, 0D, 0D, 16D, 16D, 16D);

    public SteamEngineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART_LENGTH, 1).setValue(PART_SIDE, 1).setValue(PART_Y, 0));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite());
        for (BlockPos part : partPositions(core, facing)) {
            if (!part.equals(clicked) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(clicked, core, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core, facing)) {
            level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                             CollisionContext context) { return FULL; }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position,
                            BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, position, newState, movedByPiston);
            return;
        }
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                for (BlockPos part : partPositions(core, facing)) {
                    if (!part.equals(position) && level.getBlockState(part).is(this)) {
                        level.setBlock(part, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            } finally {
                REMOVING.set(false);
            }
        }
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null) return List.of();
        return List.of(new ItemStack(ModItems.MACHINE_STEAM_ENGINE_ITEM.get()));
    }

    @Override public boolean dropFromExplosion(Explosion explosion) { return false; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        if (isCore(state)) return new SteamEngineBlockEntity(position, state);
        return isPort(state) ? new SteamEngineProxyBlockEntity(position, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_STEAM_ENGINE.get(),
                SteamEngineBlockEntity::tick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_LENGTH, PART_SIDE, PART_Y);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(PART_LENGTH) == 1 && state.getValue(PART_SIDE) == 1
                && state.getValue(PART_Y) == 0;
    }

    /** Three useful blocks hidden in a much larger machine. */
    public static boolean isPort(BlockState state) {
        return state.getValue(PART_Y) == 1 && state.getValue(PART_SIDE) == 2
                && state.getValue(PART_LENGTH) <= 2;
    }

    public static boolean canConnectAt(BlockState state, @Nullable Direction side) {
        return isPort(state) && side == state.getValue(FACING).getClockWise();
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction forward = facing.getOpposite();
        Direction side = facing.getClockWise();
        return position.relative(forward, 1 - state.getValue(PART_LENGTH))
                .relative(side, 1 - state.getValue(PART_SIDE)).below(state.getValue(PART_Y));
    }

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        Direction forward = facing.getOpposite();
        Direction side = facing.getClockWise();
        List<BlockPos> positions = new ArrayList<>(42);
        for (int y = 0; y <= 1; y++) {
            for (int length = -1; length <= 5; length++) {
                for (int cross = -1; cross <= 1; cross++) {
                    positions.add(core.relative(forward, length).relative(side, cross).above(y));
                }
            }
        }
        return positions;
    }

    /** Places where pipes are less decorative. */
    public static List<Connection> connections(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        List<Connection> connections = new ArrayList<>(3);
        for (int alongFacing = -1; alongFacing <= 1; alongFacing++) {
            BlockPos port = core.relative(side).relative(facing, alongFacing).above();
            connections.add(new Connection(port, port.relative(side), side));
        }
        return connections;
    }

    private BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        BlockPos delta = part.subtract(core);
        Direction forward = facing.getOpposite();
        Direction side = facing.getClockWise();
        int length = delta.getX() * forward.getStepX() + delta.getZ() * forward.getStepZ();
        int cross = delta.getX() * side.getStepX() + delta.getZ() * side.getStepZ();
        return defaultBlockState().setValue(FACING, facing).setValue(PART_LENGTH, length + 1)
                .setValue(PART_SIDE, cross + 1).setValue(PART_Y, delta.getY());
    }

    public record Connection(BlockPos port, BlockPos target, Direction outward) { }
}
