package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.RefineryBlockEntity;
import com.hbm.ntm.blockentity.RefineryProxyBlockEntity;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Source Oil Refinery volume: three by three cells and nine blocks tall. */
public final class RefineryBlock extends BaseEntityBlock {
    public static final MapCodec<RefineryBlock> CODEC = simpleCodec(RefineryBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, 2);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 8);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = box(0D, 0D, 0D, 16D, 16D, 16D);

    public RefineryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART_X, 1).setValue(PART_Z, 1).setValue(PART_Y, 0));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite());
        for (BlockPos part : partPositions(core)) {
            if (!part.equals(clicked) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(clicked, core, facing);
    }

    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core)) {
            level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        }
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(core) instanceof RefineryBlockEntity refinery) {
            refinery.setCustomName(stack.getHoverName());
        }
        if (level.getBlockEntity(core) instanceof RefineryBlockEntity refinery) {
            refinery.restoreFromItem(stack);
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                             CollisionContext context) { return FULL; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                          Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof RefineryBlockEntity refinery) {
            serverPlayer.openMenu(refinery, buffer -> buffer.writeBlockPos(core));
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
                if (!level.isClientSide && level.getBlockEntity(core) instanceof RefineryBlockEntity refinery) {
                    Containers.dropContents(level, core, refinery);
                    refinery.clearContent();
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

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null) return List.of();
        RefineryBlockEntity refinery = findRefinery(state, params);
        return List.of(refinery == null
                ? new ItemStack(ModItems.MACHINE_REFINERY_ITEM.get()) : refinery.machineDrop());
    }

    @Nullable private static RefineryBlockEntity findRefinery(BlockState state, LootParams.Builder params) {
        BlockEntity supplied = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (supplied instanceof RefineryBlockEntity refinery) return refinery;
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) return null;
        BlockPos core = corePosition(BlockPos.containing(origin), state);
        return params.getLevel().getBlockEntity(core) instanceof RefineryBlockEntity refinery
                ? refinery : null;
    }

    @Override public boolean dropFromExplosion(Explosion explosion) { return false; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        if (isCore(state)) return new RefineryBlockEntity(position, state);
        return isProxy(state) ? new RefineryProxyBlockEntity(position, state) : null;
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_REFINERY.get(),
                RefineryBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Z, PART_Y);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(PART_X) == 1 && state.getValue(PART_Z) == 1
                && state.getValue(PART_Y) == 0;
    }

    public static boolean isProxy(BlockState state) {
        return state.getValue(PART_Y) == 0 && state.getValue(PART_X) != 1
                && state.getValue(PART_Z) != 1;
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        return position.relative(side, 1 - state.getValue(PART_X))
                .relative(facing, 1 - state.getValue(PART_Z)).below(state.getValue(PART_Y));
    }

    public static List<BlockPos> partPositions(BlockPos core) {
        List<BlockPos> positions = new ArrayList<>(81);
        for (int y = 0; y <= 8; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) positions.add(core.offset(x, y, z));
            }
        }
        return positions;
    }

    public static boolean canConnectAt(BlockState state, @Nullable Direction direction) {
        if (!isProxy(state) || direction == null || !direction.getAxis().isHorizontal()) return false;
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        int x = state.getValue(PART_X);
        int z = state.getValue(PART_Z);
        return x == 0 && direction == side.getOpposite()
                || x == 2 && direction == side
                || z == 0 && direction == facing.getOpposite()
                || z == 2 && direction == facing;
    }

    private BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        int x = delta.getX() * side.getStepX() + delta.getZ() * side.getStepZ() + 1;
        int z = delta.getX() * facing.getStepX() + delta.getZ() * facing.getStepZ() + 1;
        return defaultBlockState().setValue(FACING, facing).setValue(PART_X, x)
                .setValue(PART_Z, z).setValue(PART_Y, delta.getY());
    }
}
