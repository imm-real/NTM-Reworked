package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.GasTurbineBlockEntity;
import com.hbm.ntm.blockentity.GasTurbineProxyBlockEntity;
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

/** Source 10x3x3 combined-cycle gas turbine. Wide load coming through. */
public final class GasTurbineBlock extends BaseEntityBlock {
    public static final MapCodec<GasTurbineBlock> CODEC = simpleCodec(GasTurbineBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Source forward range -1..1. */
    public static final IntegerProperty PART_LENGTH = IntegerProperty.create("part_length", 0, 2);
    /** Source clockwise-side range -5..4, encoded as 0..9. */
    public static final IntegerProperty PART_SIDE = IntegerProperty.create("part_side", 0, 9);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 2);

    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = Shapes.block();

    public GasTurbineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART_LENGTH, 1).setValue(PART_SIDE, 5).setValue(PART_Y, 0));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        // Source getOffset() == 1: the placed block is the forward end, not the core.
        BlockPos core = clicked.relative(facing.getOpposite());
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
        if (level.getBlockEntity(core) instanceof GasTurbineBlockEntity turbine
                && stack.has(DataComponents.CUSTOM_NAME)) turbine.setCustomName(stack.getHoverName());
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                             CollisionContext context) { return FULL; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof GasTurbineBlockEntity turbine) {
            serverPlayer.openMenu(turbine, buffer -> buffer.writeBlockPos(core));
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
            if (!level.isClientSide && level.getBlockEntity(core) instanceof GasTurbineBlockEntity turbine) {
                Containers.dropContents(level, core, turbine);
                turbine.clearContent();
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
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null) return List.of();
        return List.of(new ItemStack(ModItems.MACHINE_TURBINE_GAS_ITEM.get()));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        if (isCore(state)) return new GasTurbineBlockEntity(position, state);
        return port(state) != Port.NONE ? new GasTurbineProxyBlockEntity(position, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_TURBINE_GAS.get(),
                GasTurbineBlockEntity::tick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_LENGTH, PART_SIDE, PART_Y);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(PART_LENGTH) == 1 && state.getValue(PART_SIDE) == 5
                && state.getValue(PART_Y) == 0;
    }

    public static Port port(BlockState state) {
        int length = state.getValue(PART_LENGTH) - 1;
        int side = state.getValue(PART_SIDE) - 5;
        int y = state.getValue(PART_Y);
        if (y == 0 && Math.abs(length) == 1 && side == 1) return Port.FUEL_LUBE;
        if (y == 0 && Math.abs(length) == 1 && side == -4) return Port.WATER;
        if (y == 1 && length == 0 && side == -5) return Port.STEAM;
        if (y == 1 && length == 0 && side == 4) return Port.POWER;
        return Port.NONE;
    }

    public static boolean canFluidConnectAt(BlockState state, @Nullable Direction side) {
        Port port = port(state);
        return port.fluid && side == outwardDirection(state);
    }

    public static boolean canPowerConnectAt(BlockState state, @Nullable Direction side) {
        return port(state) == Port.POWER && side == outwardDirection(state);
    }

    @Nullable
    public static Direction outwardDirection(BlockState state) {
        Port port = port(state);
        Direction facing = state.getValue(FACING);
        Direction lateral = facing.getClockWise();
        if (port == Port.FUEL_LUBE || port == Port.WATER) {
            return state.getValue(PART_LENGTH) == 2 ? facing : facing.getOpposite();
        }
        if (port == Port.STEAM) return lateral.getOpposite();
        if (port == Port.POWER) return lateral;
        return null;
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction lateral = facing.getClockWise();
        return position.relative(facing, 1 - state.getValue(PART_LENGTH))
                .relative(lateral, 5 - state.getValue(PART_SIDE)).below(state.getValue(PART_Y));
    }

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        Direction lateral = facing.getClockWise();
        List<BlockPos> positions = new ArrayList<>(90);
        for (int y = 0; y <= 2; y++) {
            for (int length = -1; length <= 1; length++) {
                for (int side = -5; side <= 4; side++) {
                    positions.add(core.relative(facing, length).relative(lateral, side).above(y));
                }
            }
        }
        return positions;
    }

    public static List<Connection> fluidConnections(BlockPos core, Direction facing) {
        Direction lateral = facing.getClockWise();
        List<Connection> connections = new ArrayList<>(5);
        for (int length : new int[]{-1, 1}) {
            Direction outward = length > 0 ? facing : facing.getOpposite();
            BlockPos fuel = core.relative(facing, length).relative(lateral);
            BlockPos water = core.relative(facing, length).relative(lateral, -4);
            connections.add(new Connection(fuel, fuel.relative(outward), outward, Port.FUEL_LUBE));
            connections.add(new Connection(water, water.relative(outward), outward, Port.WATER));
        }
        BlockPos steam = core.relative(lateral, -5).above();
        connections.add(new Connection(steam, steam.relative(lateral.getOpposite()),
                lateral.getOpposite(), Port.STEAM));
        return connections;
    }

    public static Connection powerConnection(BlockPos core, Direction facing) {
        Direction lateral = facing.getClockWise();
        BlockPos port = core.relative(lateral, 4).above();
        return new Connection(port, port.relative(lateral), lateral, Port.POWER);
    }

    public BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction lateral = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        int length = delta.getX() * facing.getStepX() + delta.getZ() * facing.getStepZ();
        int side = delta.getX() * lateral.getStepX() + delta.getZ() * lateral.getStepZ();
        return defaultBlockState().setValue(FACING, facing).setValue(PART_LENGTH, length + 1)
                .setValue(PART_SIDE, side + 5).setValue(PART_Y, delta.getY());
    }

    public enum Port {
        NONE(false), FUEL_LUBE(true), WATER(true), STEAM(true), POWER(false);
        private final boolean fluid;
        Port(boolean fluid) { this.fluid = fluid; }
    }

    public record Connection(BlockPos port, BlockPos target, Direction outward, Port portType) { }
}
