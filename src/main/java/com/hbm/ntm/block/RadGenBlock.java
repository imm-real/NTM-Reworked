package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.RadGenBlockEntity;
import com.hbm.ntm.blockentity.RadGenProxyBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A 3x3x6 RadGen pretending to be one block in the creative menu. */
public final class RadGenBlock extends BaseEntityBlock {
    public static final MapCodec<RadGenBlock> CODEC = simpleCodec(RadGenBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Forward range -3..2, shifted because block states dislike negative feelings. */
    public static final IntegerProperty LONGITUDINAL = IntegerProperty.create("part_long", 0, 5);
    public static final IntegerProperty SIDE = IntegerProperty.create("part_side", 0, 2);
    public static final IntegerProperty Y = IntegerProperty.create("part_y", 0, 2);

    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = Shapes.block();

    public RadGenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(LONGITUDINAL, 3).setValue(SIDE, 1).setValue(Y, 0));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        // The item is placed on the +2 end, not at the core.
        BlockPos core = clicked.relative(facing.getOpposite(), 2);
        for (BlockPos part : partPositions(core, facing)) {
            if (!part.equals(clicked) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(clicked, core, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core, facing)) {
            level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(core) instanceof RadGenBlockEntity radGen
                && stack.has(DataComponents.CUSTOM_NAME)) {
            radGen.setCustomName(stack.getHoverName());
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FULL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                               Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof RadGenBlockEntity radGen) {
            serverPlayer.openMenu(radGen, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position,
                            BlockState newState, boolean moved) {
        if (state.is(newState.getBlock()) || REMOVING.get()) {
            super.onRemove(state, level, position, newState, moved);
            return;
        }
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        REMOVING.set(true);
        try {
            if (!level.isClientSide && level.getBlockEntity(core) instanceof RadGenBlockEntity radGen) {
                Containers.dropContents(level, core, radGen);
                radGen.clearContent();
            }
            for (BlockPos part : partPositions(core, facing)) {
                if (!part.equals(position) && level.getBlockState(part).is(this)) level.removeBlock(part, false);
            }
        } finally {
            REMOVING.set(false);
        }
        super.onRemove(state, level, position, newState, moved);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(state.getBlock()));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (isCore(state)) return new RadGenBlockEntity(pos, state);
        return isProxy(state) ? new RadGenProxyBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_RADGEN.get(),
                RadGenBlockEntity::tick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LONGITUDINAL, SIDE, Y);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(LONGITUDINAL) == 3
                && state.getValue(SIDE) == 1 && state.getValue(Y) == 0;
    }

    /** Three cells allowed to impersonate the core for inventory and power. */
    public static boolean isProxy(BlockState state) {
        int longitudinal = state.getValue(LONGITUDINAL) - 3;
        int side = state.getValue(SIDE) - 1;
        return state.getValue(Y) == 0
                && ((longitudinal == -3 && side == 0) || (longitudinal == 0 && Math.abs(side) == 1));
    }

    public static boolean isPowerPort(BlockState state) {
        return state.getValue(Y) == 0
                && state.getValue(LONGITUDINAL) == 0 && state.getValue(SIDE) == 1;
    }

    public static boolean canConnectPower(BlockState state, @Nullable Direction side) {
        return side != null && isPowerPort(state)
                && side == state.getValue(FACING).getOpposite();
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction lateral = facing.getClockWise();
        int longitudinal = state.getValue(LONGITUDINAL) - 3;
        int side = state.getValue(SIDE) - 1;
        return position.relative(facing, -longitudinal)
                .relative(lateral, -side).below(state.getValue(Y));
    }

    public static BlockPos powerTarget(BlockPos core, Direction facing) {
        return core.relative(facing.getOpposite(), 4);
    }

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        Direction lateral = facing.getClockWise();
        List<BlockPos> parts = new ArrayList<>(54);
        for (int y = 0; y <= 2; y++) {
            for (int longitudinal = -3; longitudinal <= 2; longitudinal++) {
                for (int side = -1; side <= 1; side++) {
                    parts.add(core.above(y).relative(facing, longitudinal).relative(lateral, side));
                }
            }
        }
        return parts;
    }

    public BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction lateral = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        int longitudinal = delta.getX() * facing.getStepX() + delta.getZ() * facing.getStepZ();
        int side = delta.getX() * lateral.getStepX() + delta.getZ() * lateral.getStepZ();
        return defaultBlockState().setValue(FACING, facing)
                .setValue(LONGITUDINAL, longitudinal + 3)
                .setValue(SIDE, side + 1).setValue(Y, delta.getY());
    }
}
