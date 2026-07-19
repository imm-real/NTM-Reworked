package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.FensuBlockEntity;
import com.hbm.ntm.blockentity.FensuProxyBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 9x5x10 BigInteger energy store with six bottom ports. */
public final class FensuBlock extends BaseEntityBlock {
    public static final MapCodec<FensuBlock> CODEC = simpleCodec(FensuBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X = IntegerProperty.create("part_x", 0, 8);
    public static final IntegerProperty Y = IntegerProperty.create("part_y", 0, 9);
    public static final IntegerProperty Z = IntegerProperty.create("part_z", 0, 4);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = Shapes.block();

    public FensuBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(X, 4).setValue(Y, 0).setValue(Z, 2));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
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
        if (level.getBlockEntity(core) instanceof FensuBlockEntity fensu) {
            if (stack.has(DataComponents.CUSTOM_NAME)) fensu.setCustomName(stack.getHoverName());
            fensu.restoreFromItem(stack);
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                             CollisionContext context) { return FULL; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                               Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof FensuBlockEntity fensu) {
            serverPlayer.openMenu(fensu, buffer -> buffer.writeBlockPos(core));
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
            if (!level.isClientSide && level.getBlockEntity(core) instanceof FensuBlockEntity fensu) {
                Containers.dropContents(level, core, fensu);
                fensu.clearContent();
                if (level instanceof ServerLevel server) fensu.removeEnergyNode(server);
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

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        FensuBlockEntity fensu = findFensu(state, params);
        return List.of(fensu == null ? new ItemStack(ModItems.MACHINE_BATTERY_REDD_ITEM.get()) : fensu.machineDrop());
    }

    @Nullable
    private static FensuBlockEntity findFensu(BlockState state, LootParams.Builder params) {
        BlockEntity supplied = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (supplied instanceof FensuBlockEntity fensu) return fensu;
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) return null;
        BlockPos core = corePosition(BlockPos.containing(origin), state);
        return params.getLevel().getBlockEntity(core) instanceof FensuBlockEntity fensu ? fensu : null;
    }

    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return isPort(state); }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos position) {
        if (!isPort(state)) return 0;
        BlockPos core = corePosition(position, state);
        return level.getBlockEntity(core) instanceof FensuBlockEntity fensu ? fensu.comparatorOutput() : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        if (isCore(state)) return new FensuBlockEntity(position, state);
        return isPort(state) ? new FensuProxyBlockEntity(position, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_BATTERY_REDD.get(),
                FensuBlockEntity::tick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Y, Z);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(X) == 4 && state.getValue(Y) == 0 && state.getValue(Z) == 2;
    }

    public static boolean isPort(BlockState state) {
        if (state.getValue(Y) != 0) return false;
        int x = state.getValue(X) - 4;
        int z = state.getValue(Z) - 2;
        return Math.abs(x) == 2 && Math.abs(z) == 2 || Math.abs(x) == 4 && z == 0;
    }

    public static boolean canConnectAt(BlockState state, @Nullable Direction side) {
        return isPort(state) && side != null && side == portDirection(state);
    }

    public static Direction portDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction clockwise = facing.getClockWise();
        int x = state.getValue(X) - 4;
        int z = state.getValue(Z) - 2;
        if (z > 0) return facing;
        if (z < 0) return facing.getOpposite();
        return x > 0 ? clockwise : clockwise.getOpposite();
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        int x = state.getValue(X) - 4;
        int z = state.getValue(Z) - 2;
        return position.relative(side, -x).relative(facing, -z).below(state.getValue(Y));
    }

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        List<BlockPos> result = new ArrayList<>(450);
        for (int y = 0; y <= 9; y++) for (int x = -4; x <= 4; x++) for (int z = -2; z <= 2; z++) {
            result.add(core.relative(side, x).relative(facing, z).above(y));
        }
        return result;
    }

    public static BlockPos[] portPositions(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        return new BlockPos[]{
                core.relative(facing, 2).relative(side, 2),
                core.relative(facing, 2).relative(side.getOpposite(), 2),
                core.relative(facing.getOpposite(), 2).relative(side, 2),
                core.relative(facing.getOpposite(), 2).relative(side.getOpposite(), 2),
                core.relative(side, 4),
                core.relative(side.getOpposite(), 4)
        };
    }

    private BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        int x = delta.getX() * side.getStepX() + delta.getZ() * side.getStepZ();
        int z = delta.getX() * facing.getStepX() + delta.getZ() * facing.getStepZ();
        return defaultBlockState().setValue(FACING, facing).setValue(X, x + 4)
                .setValue(Y, delta.getY()).setValue(Z, z + 2);
    }
}
