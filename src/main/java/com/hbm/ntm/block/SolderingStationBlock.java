package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.SolderingStationBlockEntity;
import com.hbm.ntm.blockentity.SolderingStationProxyBlockEntity;
import com.hbm.ntm.energy.HeNetworkManager;
import com.hbm.ntm.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class SolderingStationBlock extends BaseEntityBlock {
    public static final MapCodec<SolderingStationBlock> CODEC = simpleCodec(SolderingStationBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty FORWARD = IntegerProperty.create("forward", 0, 1);
    public static final IntegerProperty SIDE = IntegerProperty.create("side", 0, 1);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public SolderingStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(FORWARD, 0).setValue(SIDE, 0));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos core = context.getClickedPos();
        for (BlockPos part : partPositions(core, facing)) {
            if (!part.equals(core) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(core, core, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core, facing)) level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(core) instanceof SolderingStationBlockEntity station) {
            station.setCustomName(stack.getHoverName());
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos core = corePosition(pos, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof SolderingStationBlockEntity station) {
            serverPlayer.openMenu(station, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (state.is(newState.getBlock())) { super.onRemove(state, level, pos, newState, moved); return; }
        BlockPos core = corePosition(pos, state);
        Direction facing = state.getValue(FACING);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                if (!level.isClientSide && level.getBlockEntity(core) instanceof SolderingStationBlockEntity station) {
                    Containers.dropContents(level, core, station);
                    station.clearContent();
                    if (level instanceof ServerLevel serverLevel) HeNetworkManager.get(serverLevel).destroyNode(core);
                }
                for (BlockPos part : partPositions(core, facing)) {
                    if (!part.equals(pos) && level.getBlockState(part).is(this)) {
                        level.setBlock(part, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            } finally { REMOVING.set(false); }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isCore(state) ? new SolderingStationBlockEntity(pos, state)
                : new SolderingStationProxyBlockEntity(pos, state);
    }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_SOLDERING_STATION.get(),
                SolderingStationBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORWARD, SIDE);
    }

    public static boolean isCore(BlockState state) { return state.getValue(FORWARD) == 0 && state.getValue(SIDE) == 0; }

    public static BlockPos corePosition(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        return pos.relative(facing, state.getValue(FORWARD))
                .relative(side.getOpposite(), state.getValue(SIDE));
    }

    public static BlockPos[] partPositions(BlockPos core, Direction facing) {
        Direction forward = facing.getOpposite();
        Direction side = facing.getClockWise();
        return new BlockPos[]{core, core.relative(forward), core.relative(side),
                core.relative(forward).relative(side)};
    }

    public static boolean canConnectAt(BlockState state, Direction direction) {
        if (direction == null || !direction.getAxis().isHorizontal()) return false;
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        return direction == (state.getValue(FORWARD) == 0 ? facing : facing.getOpposite())
                || direction == (state.getValue(SIDE) == 0 ? side.getOpposite() : side);
    }

    private BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction forwardDirection = facing.getOpposite();
        Direction side = facing.getClockWise();
        int forward = part.equals(core) || part.equals(core.relative(side)) ? 0 : 1;
        int lateral = part.equals(core) || part.equals(core.relative(forwardDirection)) ? 0 : 1;
        return defaultBlockState().setValue(FACING, facing).setValue(FORWARD, forward).setValue(SIDE, lateral);
    }
}
