package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.PumpBlockEntity;
import com.hbm.ntm.blockentity.PumpProxyBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Shared 3x4x3 body for both groundwater pump tiers. */
public final class PumpBlock extends BaseEntityBlock {
    public static final MapCodec<PumpBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(), Codec.BOOL.fieldOf("electric").forGetter(PumpBlock::electric)
    ).apply(instance, PumpBlock::new));
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 3);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, 2);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = box(0D, 0D, 0D, 16D, 16D, 16D);

    private final boolean electric;

    public PumpBlock(Properties properties, boolean electric) {
        super(properties);
        this.electric = electric;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART_X, 1).setValue(PART_Y, 0).setValue(PART_Z, 1));
    }

    public boolean electric() { return electric; }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite());
        for (BlockPos part : partPositions(core)) {
            if (!part.equals(clicked) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(clicked, core, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core)) {
            level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                             CollisionContext context) { return FULL; }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position,
                            BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, position, newState, movedByPiston);
            return;
        }
        BlockPos core = corePosition(position, state);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
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
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null) return List.of();
        return List.of(new ItemStack(electric ? ModItems.PUMP_ELECTRIC_ITEM.get() : ModItems.PUMP_STEAM_ITEM.get()));
    }

    @Override public boolean dropFromExplosion(Explosion explosion) { return false; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        if (isCore(state)) return new PumpBlockEntity(position, state);
        return isPort(state) ? new PumpProxyBlockEntity(position, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.PUMP.get(), PumpBlockEntity::tick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y, PART_Z);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(PART_X) == 1 && state.getValue(PART_Y) == 0 && state.getValue(PART_Z) == 1;
    }

    public static boolean isPort(BlockState state) {
        if (state.getValue(PART_Y) != 0) return false;
        int x = state.getValue(PART_X);
        int z = state.getValue(PART_Z);
        return x == 1 && z != 1 || z == 1 && x != 1;
    }

    @Nullable
    public static Direction outward(BlockState state) {
        if (!isPort(state)) return null;
        int x = state.getValue(PART_X);
        int z = state.getValue(PART_Z);
        if (x == 0) return Direction.WEST;
        if (x == 2) return Direction.EAST;
        if (z == 0) return Direction.NORTH;
        return Direction.SOUTH;
    }

    public static boolean canConnectAt(BlockState state, @Nullable Direction side) {
        return side != null && side == outward(state);
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        return position.offset(1 - state.getValue(PART_X), -state.getValue(PART_Y),
                1 - state.getValue(PART_Z));
    }

    public static List<BlockPos> partPositions(BlockPos core) {
        List<BlockPos> positions = new ArrayList<>(36);
        for (int y = 0; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) positions.add(core.offset(x, y, z));
            }
        }
        return positions;
    }

    public static List<Connection> connections(BlockPos core) {
        List<Connection> connections = new ArrayList<>(4);
        for (Direction outward : Direction.Plane.HORIZONTAL) {
            BlockPos port = core.relative(outward);
            connections.add(new Connection(port, port.relative(outward), outward));
        }
        return connections;
    }

    private BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        BlockPos delta = part.subtract(core);
        return defaultBlockState().setValue(FACING, facing)
                .setValue(PART_X, delta.getX() + 1)
                .setValue(PART_Y, delta.getY())
                .setValue(PART_Z, delta.getZ() + 1);
    }

    public record Connection(BlockPos port, BlockPos target, Direction outward) { }
}
