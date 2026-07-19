package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.ArcWelderBlockEntity;
import com.hbm.ntm.blockentity.ArcWelderProxyBlockEntity;
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

import java.util.ArrayList;
import java.util.List;

/** Source twelve-block Arc Welder: three wide, two deep, and two high. */
public final class ArcWelderBlock extends BaseEntityBlock {
    public static final MapCodec<ArcWelderBlock> CODEC = simpleCodec(ArcWelderBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty LATERAL = IntegerProperty.create("lateral", 0, 2);
    public static final IntegerProperty DEPTH = IntegerProperty.create("depth", 0, 1);
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 0, 1);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = box(0, 0, 0, 16, 16, 16);
    private static final int[] RED_SLOTS = {0, 3};
    private static final int[] YELLOW_SLOTS = {1, 3};
    private static final int[] GREEN_SLOTS = {2, 3};
    private static final int[] NO_SLOTS = {};

    public ArcWelderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(LATERAL, 1).setValue(DEPTH, 0).setValue(HEIGHT, 0));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos core = context.getClickedPos();
        for (BlockPos part : partPositions(core, facing)) {
            if (!part.equals(core) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(core, core, facing);
    }

    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core, facing)) {
            level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        }
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(core) instanceof ArcWelderBlockEntity welder) {
            welder.setCustomName(stack.getHoverName());
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) { return FULL; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                          Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof ArcWelderBlockEntity welder) {
            serverPlayer.openMenu(welder, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos position,
                                      BlockState newState, boolean moved) {
        if (state.is(newState.getBlock())) { super.onRemove(state, level, position, newState, moved); return; }
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                if (!level.isClientSide && level.getBlockEntity(core) instanceof ArcWelderBlockEntity welder) {
                    Containers.dropContents(level, core, welder);
                    welder.clearContent();
                    if (level instanceof ServerLevel serverLevel) HeNetworkManager.get(serverLevel).destroyNode(core);
                }
                for (BlockPos part : partPositions(core, facing)) {
                    if (!part.equals(position) && level.getBlockState(part).is(this)) {
                        level.setBlock(part, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            } finally { REMOVING.set(false); }
        }
        super.onRemove(state, level, position, newState, moved);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isCore(state) ? new ArcWelderBlockEntity(pos, state) : new ArcWelderProxyBlockEntity(pos, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_ARC_WELDER.get(),
                ArcWelderBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LATERAL, DEPTH, HEIGHT);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(LATERAL) == 1 && state.getValue(DEPTH) == 0 && state.getValue(HEIGHT) == 0;
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        return position.relative(side, 1 - state.getValue(LATERAL))
                .relative(facing, state.getValue(DEPTH)).below(state.getValue(HEIGHT));
    }

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        List<BlockPos> positions = new ArrayList<>(12);
        for (int height = 0; height <= 1; height++) {
            for (int depth = 0; depth <= 1; depth++) {
                for (int lateral = -1; lateral <= 1; lateral++) {
                    positions.add(core.relative(side, lateral).relative(facing.getOpposite(), depth).above(height));
                }
            }
        }
        return positions;
    }

    public static boolean canConnectAt(BlockState state, Direction direction) {
        if (direction == null || !direction.getAxis().isHorizontal() || state.getValue(HEIGHT) != 0) return false;
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        int lateral = state.getValue(LATERAL);
        int depth = state.getValue(DEPTH);
        return depth == 0 && direction == facing
                || depth == 1 && direction == facing.getOpposite()
                || lateral == 0 && direction == side.getOpposite()
                || lateral == 2 && direction == side;
    }

    public static int[] automationSlots(BlockState state) {
        if (state.getValue(HEIGHT) != 0) return NO_SLOTS;
        BlockPos local = new BlockPos(state.getValue(LATERAL) - 1, 0, state.getValue(DEPTH));
        if (local.getX() == 1 && local.getZ() == 0 || local.getX() == -1 && local.getZ() == 1) return RED_SLOTS;
        if (local.getX() == 0 && local.getZ() == 1) return YELLOW_SLOTS;
        if (local.getX() == -1 && local.getZ() == 0 || local.getX() == 1 && local.getZ() == 1) return GREEN_SLOTS;
        return NO_SLOTS;
    }

    private BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        int lateral = delta.getX() * side.getStepX() + delta.getZ() * side.getStepZ() + 1;
        int depth = -(delta.getX() * facing.getStepX() + delta.getZ() * facing.getStepZ());
        int height = delta.getY();
        return defaultBlockState().setValue(FACING, facing).setValue(LATERAL, lateral)
                .setValue(DEPTH, depth).setValue(HEIGHT, height);
    }
}
