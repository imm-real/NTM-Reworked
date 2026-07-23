package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.RadioTelexBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class RadioTelexBlock extends BaseEntityBlock {
    public static final MapCodec<RadioTelexBlock> CODEC = simpleCodec(RadioTelexBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = box(0, 0, 0, 16, 16, 16);

    public RadioTelexBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
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
        BlockPos side = core.relative(facing.getClockWise());
        if (!context.getLevel().getBlockState(side).canBeReplaced(context)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        level.setBlock(core, defaultBlockState().setValue(FACING, facing), Block.UPDATE_ALL);
        level.setBlock(core.relative(facing.getClockWise()),
                defaultBlockState().setValue(FACING, facing).setValue(PART, 1), Block.UPDATE_ALL);
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
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof RadioTelexBlockEntity telex) {
            serverPlayer.openMenu(telex, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position,
                            BlockState next, boolean moved) {
        if (state.is(next.getBlock())) {
            super.onRemove(state, level, position, next, moved);
            return;
        }
        BlockPos core = corePosition(position, state);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                for (BlockPos part : partPositions(core, state.getValue(FACING))) {
                    if (!part.equals(position) && level.getBlockState(part).is(this)) {
                        level.setBlock(part, Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            } finally {
                REMOVING.set(false);
            }
        }
        super.onRemove(state, level, position, next, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return isCore(state) ? new RadioTelexBlockEntity(position, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.RADIO_TELEX.get(),
                RadioTelexBlockEntity::tick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(PART) == 0;
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        return state.getValue(PART) == 0 ? position
                : position.relative(state.getValue(FACING).getCounterClockWise());
    }

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        return List.of(core, core.relative(facing.getClockWise()));
    }
}
