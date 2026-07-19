package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.AirIntakeBlockEntity;
import com.hbm.ntm.blockentity.AirIntakeProxyBlockEntity;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Two by two machine for collecting premium atmosphere. */
public final class AirIntakeBlock extends BaseEntityBlock {
    public static final MapCodec<AirIntakeBlock> CODEC = simpleCodec(AirIntakeBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Front row or back row. Numbers were selected by committee. */
    public static final IntegerProperty BACK = IntegerProperty.create("part_back", 0, 1);
    /** Left-ish or right-ish. */
    public static final IntegerProperty SIDE = IntegerProperty.create("part_side", 0, 1);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = Shapes.block();

    public AirIntakeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(BACK, 0).setValue(SIDE, 0));
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
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                             CollisionContext context) { return FULL; }

    @Override protected void onRemove(BlockState state, Level level, BlockPos position,
                                      BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock()) || REMOVING.get()) {
            super.onRemove(state, level, position, newState, movedByPiston);
            return;
        }
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        REMOVING.set(true);
        try {
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

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.EXPLOSION_RADIUS)
                != null) return List.of();
        return List.of(new ItemStack(ModItems.MACHINE_INTAKE_ITEM.get()));
    }

    @Override public boolean dropFromExplosion(Explosion explosion) { return false; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return isCore(state) ? new AirIntakeBlockEntity(position, state)
                : new AirIntakeProxyBlockEntity(position, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_INTAKE.get(),
                AirIntakeBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
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

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        Direction clockwise = facing.getClockWise();
        List<BlockPos> positions = new ArrayList<>(4);
        for (int back = 0; back <= 1; back++) {
            for (int side = 0; side <= 1; side++) {
                positions.add(core.relative(facing.getOpposite(), back).relative(clockwise, side));
            }
        }
        return positions;
    }

    /** Eight holes where the air paperwork exits. */
    public static List<Connection> connections(BlockPos core, Direction facing) {
        Direction clockwise = facing.getClockWise();
        return List.of(
                new Connection(core.relative(facing), facing),
                new Connection(core.relative(facing).relative(clockwise), facing),
                new Connection(core.relative(facing.getOpposite(), 2), facing.getOpposite()),
                new Connection(core.relative(facing.getOpposite(), 2).relative(clockwise), facing.getOpposite()),
                new Connection(core.relative(clockwise, 2), clockwise),
                new Connection(core.relative(clockwise, 2).relative(facing.getOpposite()), clockwise),
                new Connection(core.relative(clockwise.getOpposite()), clockwise.getOpposite()),
                new Connection(core.relative(clockwise.getOpposite()).relative(facing.getOpposite()),
                        clockwise.getOpposite())
        );
    }

    private BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction clockwise = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        int back = -(delta.getX() * facing.getStepX() + delta.getZ() * facing.getStepZ());
        int side = delta.getX() * clockwise.getStepX() + delta.getZ() * clockwise.getStepZ();
        return defaultBlockState().setValue(FACING, facing).setValue(BACK, back).setValue(SIDE, side);
    }

    public record Connection(BlockPos target, Direction outward) { }
}
