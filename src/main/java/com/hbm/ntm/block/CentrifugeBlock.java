package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.CentrifugeBlockEntity;
import com.hbm.ntm.blockentity.CentrifugeProxyBlockEntity;
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

import java.util.List;

/** One centrifuge core and three blocks pretending to be useful. */
public final class CentrifugeBlock extends BaseEntityBlock {
    public static final MapCodec<CentrifugeBlock> CODEC = simpleCodec(CentrifugeBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 3);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape CORE_SHAPE = box(0D, 0D, 0D, 16D, 15.984D, 16D);
    private static final VoxelShape TOWER_SHAPE = box(2D, 0D, 2D, 14D, 16D, 14D);

    public CentrifugeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART_Y, 0));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos core = context.getClickedPos();
        for (BlockPos part : partPositions(core)) {
            if (!part.equals(core) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (int partY = 0; partY <= 3; partY++) {
            level.setBlock(core.above(partY), defaultBlockState().setValue(FACING, facing)
                    .setValue(PART_Y, partY), Block.UPDATE_ALL);
        }
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(core) instanceof CentrifugeBlockEntity centrifuge) {
            centrifuge.setCustomName(stack.getHoverName());
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                             CollisionContext context) {
        return isCore(state) ? CORE_SHAPE : TOWER_SHAPE;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                          Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof CentrifugeBlockEntity centrifuge) {
            serverPlayer.openMenu(centrifuge, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos position,
                                      BlockState newState, boolean moved) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, position, newState, moved);
            return;
        }
        BlockPos core = corePosition(position, state);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                if (!level.isClientSide && level.getBlockEntity(core) instanceof CentrifugeBlockEntity centrifuge) {
                    Containers.dropContents(level, core, centrifuge);
                    centrifuge.clearContent();
                    if (level instanceof ServerLevel serverLevel) HeNetworkManager.get(serverLevel).destroyNode(core);
                }
                for (BlockPos part : partPositions(core)) {
                    if (!part.equals(position) && level.getBlockState(part).is(this)) {
                        level.setBlock(part, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            } finally {
                REMOVING.set(false);
            }
        }
        super.onRemove(state, level, position, newState, moved);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return isCore(state) ? new CentrifugeBlockEntity(position, state)
                : new CentrifugeProxyBlockEntity(position, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_CENTRIFUGE.get(),
                CentrifugeBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_Y);
    }

    public static boolean isCore(BlockState state) { return state.getValue(PART_Y) == 0; }
    public static BlockPos corePosition(BlockPos position, BlockState state) {
        return position.below(state.getValue(PART_Y));
    }
    public static List<BlockPos> partPositions(BlockPos core) {
        return List.of(core, core.above(), core.above(2), core.above(3));
    }
}
