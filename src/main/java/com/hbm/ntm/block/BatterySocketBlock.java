package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.BatterySocketBlockEntity;
import com.hbm.ntm.blockentity.BatterySocketProxyBlockEntity;
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

public final class BatterySocketBlock extends BaseEntityBlock {
    public static final MapCodec<BatterySocketBlock> CODEC = simpleCodec(BatterySocketBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty BACK = IntegerProperty.create("back", 0, 1);
    public static final IntegerProperty SIDE = IntegerProperty.create("side", 0, 1);
    private static final VoxelShape SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    public BatterySocketBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BACK, 0)
                .setValue(SIDE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos core = context.getClickedPos();
        for (BlockPos part : partPositions(core, facing)) {
            if (!part.equals(core) && !context.getLevel().getBlockState(part).canBeReplaced(context)) {
                return null;
            }
        }
        return stateForPart(core, core, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core, facing)) {
            level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        }
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(core) instanceof BatterySocketBlockEntity socket) {
            socket.setCustomName(stack.getHoverName());
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                               Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof BatterySocketBlockEntity socket) {
            serverPlayer.openMenu(socket, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, position, newState, movedByPiston);
            return;
        }
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                if (!level.isClientSide && level.getBlockEntity(core) instanceof BatterySocketBlockEntity socket) {
                    Containers.dropContents(level, core, socket);
                    socket.clearContent();
                    if (level instanceof ServerLevel serverLevel) {
                        HeNetworkManager.get(serverLevel).destroyNode(core);
                    }
                }
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
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos position) {
        BlockPos core = corePosition(position, state);
        return level.getBlockEntity(core) instanceof BatterySocketBlockEntity socket
                ? socket.comparatorOutput() : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return isCore(state)
                ? new BatterySocketBlockEntity(position, state)
                : new BatterySocketProxyBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return isCore(state)
                ? createTickerHelper(type, ModBlockEntities.BATTERY_SOCKET.get(), BatterySocketBlockEntity::tick)
                : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BACK, SIDE);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(BACK) == 0 && state.getValue(SIDE) == 0;
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction clockwise = facing.getClockWise();
        return position.relative(facing, state.getValue(BACK))
                .relative(clockwise.getOpposite(), state.getValue(SIDE));
    }

    public static BlockPos[] partPositions(BlockPos core, Direction facing) {
        Direction clockwise = facing.getClockWise();
        return new BlockPos[]{
                core,
                core.relative(facing.getOpposite()),
                core.relative(clockwise),
                core.relative(facing.getOpposite()).relative(clockwise)
        };
    }

    public static boolean canConnectAt(BlockState state, Direction side) {
        if (!side.getAxis().isHorizontal()) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        Direction clockwise = facing.getClockWise();
        return side == (state.getValue(BACK) == 0 ? facing : facing.getOpposite())
                || side == (state.getValue(SIDE) == 1 ? clockwise : clockwise.getOpposite());
    }

    private BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction clockwise = facing.getClockWise();
        int back = part.equals(core) || part.equals(core.relative(clockwise)) ? 0 : 1;
        int side = part.equals(core) || part.equals(core.relative(facing.getOpposite())) ? 0 : 1;
        return defaultBlockState().setValue(FACING, facing).setValue(BACK, back).setValue(SIDE, side);
    }
}
