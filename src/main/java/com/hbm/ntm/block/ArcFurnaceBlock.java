package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.ArcFurnaceBlockEntity;
import com.hbm.ntm.blockentity.ArcFurnaceProxyBlockEntity;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Five cubic meters of arc furnace plus an unnecessary rear extension. */
public final class ArcFurnaceBlock extends BaseEntityBlock {
    public static final MapCodec<ArcFurnaceBlock> CODEC = simpleCodec(ArcFurnaceBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X = IntegerProperty.create("part_x", 0, 4);
    public static final IntegerProperty Y = IntegerProperty.create("part_y", 0, 4);
    public static final IntegerProperty Z = IntegerProperty.create("part_z", 0, 5);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = Shapes.block();

    public ArcFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(X, 2).setValue(Y, 0).setValue(Z, 3));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite(), 2);
        for (BlockPos part : partPositions(core, facing)) {
            if (!part.equals(clicked) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(clicked, core, facing);
    }

    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core, facing)) {
            level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        }
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(core) instanceof ArcFurnaceBlockEntity furnace) {
            furnace.setCustomName(stack.getHoverName());
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
                && level.getBlockEntity(core) instanceof ArcFurnaceBlockEntity furnace) {
            serverPlayer.openMenu(furnace, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos position,
                                      BlockState newState, boolean moved) {
        if (state.is(newState.getBlock()) || REMOVING.get()) {
            super.onRemove(state, level, position, newState, moved);
            return;
        }
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        REMOVING.set(true);
        try {
            if (!level.isClientSide && level.getBlockEntity(core) instanceof ArcFurnaceBlockEntity furnace) {
                Containers.dropContents(level, core, furnace);
                furnace.clearContent();
                if (level instanceof ServerLevel serverLevel) HeNetworkManager.get(serverLevel).destroyNode(core);
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
        super.onRemove(state, level, position, newState, moved);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        if (isCore(state)) return new ArcFurnaceBlockEntity(position, state);
        return isPort(state) ? new ArcFurnaceProxyBlockEntity(position, state) : null;
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_ARC_FURNACE.get(),
                ArcFurnaceBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Y, Z);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(X) == 2 && state.getValue(Y) == 0 && state.getValue(Z) == 3;
    }

    public static boolean isPort(BlockState state) {
        if (state.getValue(Y) != 0) return false;
        int x = state.getValue(X) - 2;
        int z = state.getValue(Z) - 3;
        return z == 2 && Math.abs(x) == 1 || Math.abs(x) == 2 && Math.abs(z) == 1;
    }

    public static Direction portDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        int x = state.getValue(X) - 2;
        int z = state.getValue(Z) - 3;
        if (z == 2) return facing;
        return x > 0 ? facing.getClockWise() : facing.getCounterClockWise();
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        int x = state.getValue(X) - 2;
        int z = state.getValue(Z) - 3;
        return position.relative(side, -x).relative(facing, -z).below(state.getValue(Y));
    }

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        List<BlockPos> positions = new ArrayList<>(140);
        for (int y = 0; y <= 4; y++) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                positions.add(core.relative(side, x).relative(facing, z).above(y));
            }
            // The second volume overlaps one body layer, adding only a 3x5 rear wall.
            for (int x = -1; x <= 1; x++) {
                positions.add(core.relative(side, x).relative(facing, -3).above(y));
            }
        }
        return positions;
    }

    public BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        int x = delta.getX() * side.getStepX() + delta.getZ() * side.getStepZ();
        int z = delta.getX() * facing.getStepX() + delta.getZ() * facing.getStepZ();
        return defaultBlockState().setValue(FACING, facing).setValue(X, x + 2)
                .setValue(Y, delta.getY()).setValue(Z, z + 3);
    }
}
