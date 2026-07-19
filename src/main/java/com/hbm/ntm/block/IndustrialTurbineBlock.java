package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.IndustrialTurbineBlockEntity;
import com.hbm.ntm.blockentity.IndustrialTurbineProxyBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Source 7x3x3 Industrial Steam Turbine with its six fluid ports and rear HE port. */
public final class IndustrialTurbineBlock extends BaseEntityBlock {
    public static final MapCodec<IndustrialTurbineBlock> CODEC = simpleCodec(IndustrialTurbineBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Source forward range -3..3, encoded as 0..6. */
    public static final IntegerProperty PART_LENGTH = IntegerProperty.create("part_length", 0, 6);
    /** Source clockwise-side range -1..1, encoded as 0..2. */
    public static final IntegerProperty PART_SIDE = IntegerProperty.create("part_side", 0, 2);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 2);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = Shapes.block();

    public IndustrialTurbineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART_LENGTH, 3).setValue(PART_SIDE, 1).setValue(PART_Y, 0));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite(), 3);
        for (BlockPos part : partPositions(core, facing)) {
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
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                             CollisionContext context) { return FULL; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        if (!position.equals(core.relative(facing, 3).above())) return InteractionResult.PASS;

        if (!level.isClientSide && level.getBlockEntity(core) instanceof IndustrialTurbineBlockEntity turbine) {
            if (turbine.operational()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Cannot change compressor setting while operational!").withStyle(ChatFormatting.RED), false);
            } else {
                turbine.cycleSteamGrade();
                level.playSound(null, position, ModSounds.TURBINE_LEVER.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
            }
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
            for (BlockPos part : partPositions(core, facing)) {
                if (!part.equals(position) && level.getBlockState(part).is(this)) {
                    level.setBlock(part, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
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
        if (params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null) return List.of();
        return List.of(new ItemStack(ModItems.MACHINE_INDUSTRIAL_TURBINE_ITEM.get()));
    }

    @Override public boolean dropFromExplosion(Explosion explosion) { return false; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        if (isCore(state)) return new IndustrialTurbineBlockEntity(position, state);
        return isPort(state) ? new IndustrialTurbineProxyBlockEntity(position, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_INDUSTRIAL_TURBINE.get(),
                IndustrialTurbineBlockEntity::tick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_LENGTH, PART_SIDE, PART_Y);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(PART_LENGTH) == 3 && state.getValue(PART_SIDE) == 1
                && state.getValue(PART_Y) == 0;
    }

    public static boolean isFluidPort(BlockState state) {
        int length = state.getValue(PART_LENGTH);
        int side = state.getValue(PART_SIDE);
        int y = state.getValue(PART_Y);
        return y == 0 && (length == 6 || length == 2) && side != 1
                || y == 2 && (length == 6 || length == 2) && side == 1;
    }

    public static boolean isPowerPort(BlockState state) {
        return state.getValue(PART_LENGTH) == 0 && state.getValue(PART_SIDE) == 1
                && state.getValue(PART_Y) == 1;
    }

    public static boolean isPort(BlockState state) { return isFluidPort(state) || isPowerPort(state); }

    public static boolean canFluidConnectAt(BlockState state, @Nullable Direction side) {
        return isFluidPort(state) && side == outwardDirection(state);
    }

    public static boolean canPowerConnectAt(BlockState state, @Nullable Direction side) {
        return isPowerPort(state) && side == state.getValue(FACING).getOpposite();
    }

    @Nullable
    private static Direction outwardDirection(BlockState state) {
        if (!isFluidPort(state)) return null;
        if (state.getValue(PART_Y) == 2) return Direction.UP;
        Direction clockwise = state.getValue(FACING).getClockWise();
        return state.getValue(PART_SIDE) == 2 ? clockwise : clockwise.getOpposite();
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        return position.relative(facing, 3 - state.getValue(PART_LENGTH))
                .relative(side, 1 - state.getValue(PART_SIDE)).below(state.getValue(PART_Y));
    }

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        List<BlockPos> positions = new ArrayList<>(63);
        for (int y = 0; y <= 2; y++) {
            for (int length = -3; length <= 3; length++) {
                for (int cross = -1; cross <= 1; cross++) {
                    positions.add(core.relative(facing, length).relative(side, cross).above(y));
                }
            }
        }
        return positions;
    }

    public static List<Connection> fluidConnections(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        List<Connection> connections = new ArrayList<>(6);
        for (int length : new int[]{3, -1}) {
            BlockPos center = core.relative(facing, length);
            connections.add(new Connection(center.relative(side), center.relative(side, 2), side));
            connections.add(new Connection(center.relative(side.getOpposite()),
                    center.relative(side.getOpposite(), 2), side.getOpposite()));
        }
        for (int length : new int[]{3, -1}) {
            BlockPos port = core.relative(facing, length).above(2);
            connections.add(new Connection(port, port.above(), Direction.UP));
        }
        return connections;
    }

    public static Connection powerConnection(BlockPos core, Direction facing) {
        BlockPos port = core.relative(facing.getOpposite(), 3).above();
        return new Connection(port, port.relative(facing.getOpposite()), facing.getOpposite());
    }

    public BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        int length = delta.getX() * facing.getStepX() + delta.getZ() * facing.getStepZ();
        int cross = delta.getX() * side.getStepX() + delta.getZ() * side.getStepZ();
        return defaultBlockState().setValue(FACING, facing).setValue(PART_LENGTH, length + 3)
                .setValue(PART_SIDE, cross + 1).setValue(PART_Y, delta.getY());
    }

    public record Connection(BlockPos port, BlockPos target, Direction outward) { }
}
