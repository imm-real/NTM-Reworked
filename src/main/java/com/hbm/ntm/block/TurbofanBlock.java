package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.TurbofanBlockEntity;
import com.hbm.ntm.blockentity.TurbofanProxyBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Seven wide, three long, three high and somehow still not big enough. */
public final class TurbofanBlock extends BaseEntityBlock {
    public static final MapCodec<TurbofanBlock> CODEC = simpleCodec(TurbofanBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Longways, with the negative numbers hidden from blockstates. */
    public static final IntegerProperty PART_LENGTH = IntegerProperty.create("part_length", 0, 2);
    /** Sideways, similarly laundered into positive integers. */
    public static final IntegerProperty PART_SIDE = IntegerProperty.create("part_side", 0, 6);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 2);

    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = Shapes.block();

    public TurbofanBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART_LENGTH, 1).setValue(PART_SIDE, 3).setValue(PART_Y, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite());
        for (BlockPos part : partPositions(core, facing)) {
            if (context.getLevel().isOutsideBuildHeight(part)) return null;
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
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(core) instanceof TurbofanBlockEntity turbofan) {
            turbofan.setCustomName(stack.getHoverName());
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                  CollisionContext context) {
        return FULL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof TurbofanBlockEntity turbofan) {
            serverPlayer.openMenu(turbofan, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position,
                            BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock()) || REMOVING.get()) {
            super.onRemove(state, level, position, newState, movedByPiston);
            return;
        }

        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        REMOVING.set(true);
        try {
            if (!level.isClientSide && level.getBlockEntity(core) instanceof TurbofanBlockEntity turbofan) {
                Containers.dropContents(level, core, turbofan);
                turbofan.clearContent();
            }
            for (BlockPos part : partPositions(core, facing)) {
                if (!part.equals(position) && level.getBlockState(part).is(this)) {
                    level.setBlock(part, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        } finally {
            REMOVING.set(false);
        }
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Float radius = params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
        if (radius != null && radius > 0.0F
                && params.getLevel().getRandom().nextFloat() > 1.0F / radius) return List.of();
        return List.of(new ItemStack(ModItems.MACHINE_TURBOFAN_ITEM.get()));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        if (isCore(state)) return new TurbofanBlockEntity(position, state);
        return isPort(state) ? new TurbofanProxyBlockEntity(position, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_TURBOFAN.get(),
                TurbofanBlockEntity::tick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_LENGTH, PART_SIDE, PART_Y);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(PART_LENGTH) == 1 && state.getValue(PART_SIDE) == 3
                && state.getValue(PART_Y) == 0;
    }

    /** Four plugs pretending to be twenty-one blocks. */
    public static boolean isPort(BlockState state) {
        int length = state.getValue(PART_LENGTH);
        int side = state.getValue(PART_SIDE);
        return state.getValue(PART_Y) == 0 && (length == 0 || length == 2)
                && (side == 2 || side == 3);
    }

    public static boolean canConnectAt(BlockState state, @Nullable Direction side) {
        return isPort(state) && side == outwardDirection(state);
    }

    @Nullable
    public static Direction outwardDirection(BlockState state) {
        if (!isPort(state)) return null;
        return state.getValue(PART_LENGTH) == 0
                ? state.getValue(FACING) : state.getValue(FACING).getOpposite();
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction forward = state.getValue(FACING).getOpposite();
        Direction side = state.getValue(FACING).getClockWise();
        return position.relative(forward, 1 - state.getValue(PART_LENGTH))
                .relative(side, 3 - state.getValue(PART_SIDE)).below(state.getValue(PART_Y));
    }

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        Direction forward = facing.getOpposite();
        Direction side = facing.getClockWise();
        List<BlockPos> positions = new ArrayList<>(63);
        for (int y = 0; y <= 2; y++) {
            for (int length = -1; length <= 1; length++) {
                for (int cross = -3; cross <= 3; cross++) {
                    positions.add(core.relative(forward, length).relative(side, cross).above(y));
                }
            }
        }
        return positions;
    }

    /** Where the plugs actually lead. */
    public static List<Connection> connections(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        List<Connection> connections = new ArrayList<>(4);
        for (Direction outward : new Direction[]{facing, facing.getOpposite()}) {
            BlockPos end = core.relative(outward);
            BlockPos offsetPort = end.relative(side.getOpposite());
            connections.add(new Connection(end, end.relative(outward), outward));
            connections.add(new Connection(offsetPort, offsetPort.relative(outward), outward));
        }
        return connections;
    }

    public BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction forward = facing.getOpposite();
        Direction side = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        int length = delta.getX() * forward.getStepX() + delta.getZ() * forward.getStepZ();
        int cross = delta.getX() * side.getStepX() + delta.getZ() * side.getStepZ();
        return defaultBlockState().setValue(FACING, facing).setValue(PART_LENGTH, length + 1)
                .setValue(PART_SIDE, cross + 3).setValue(PART_Y, delta.getY());
    }

    public record Connection(BlockPos port, BlockPos target, Direction outward) { }
}
