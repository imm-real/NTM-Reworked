package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.WoodBurnerBlockEntity;
import com.hbm.ntm.blockentity.WoodBurnerProxyBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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

import java.util.ArrayList;
import java.util.List;

/** 2x2x2 Wood-Burning Generator with a rear row of ports. */
public final class WoodBurnerBlock extends BaseEntityBlock {
    public static final MapCodec<WoodBurnerBlock> CODEC = simpleCodec(WoodBurnerBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART_SIDE = IntegerProperty.create("part_side", 0, 1);
    public static final IntegerProperty PART_REAR = IntegerProperty.create("part_rear", 0, 1);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 1);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = box(0D, 0D, 0D, 16D, 16D, 16D);

    public WoodBurnerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART_SIDE, 0).setValue(PART_REAR, 0).setValue(PART_Y, 0));
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
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(core) instanceof WoodBurnerBlockEntity burner) {
            burner.setCustomName(stack.getHoverName());
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
                && level.getBlockEntity(core) instanceof WoodBurnerBlockEntity burner) {
            serverPlayer.openMenu(burner, buffer -> buffer.writeBlockPos(core));
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
            if (!level.isClientSide && level.getBlockEntity(core) instanceof WoodBurnerBlockEntity burner) {
                Containers.dropContents(level, core, burner);
                burner.clearContent();
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

    @Override public void animateTick(BlockState state, Level level, BlockPos position, RandomSource random) {
        if (!isCore(state) || !(level.getBlockEntity(position) instanceof WoodBurnerBlockEntity burner)
                || burner.powerGeneration() <= 0) return;
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        level.addParticle(ParticleTypes.SMOKE,
                position.getX() + 0.5D - facing.getStepX() + side.getStepX(),
                position.getY() + 4.0D,
                position.getZ() + 0.5D - facing.getStepZ() + side.getStepZ(),
                0D, 0.05D, 0D);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return isCore(state) ? new WoodBurnerBlockEntity(position, state)
                : new WoodBurnerProxyBlockEntity(position, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_WOOD_BURNER.get(),
                WoodBurnerBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_SIDE, PART_REAR, PART_Y);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(PART_SIDE) == 0 && state.getValue(PART_REAR) == 0
                && state.getValue(PART_Y) == 0;
    }

    public static boolean isPort(BlockState state) {
        return state.getValue(PART_REAR) == 1 && state.getValue(PART_Y) == 0;
    }

    public static boolean canConnectAt(BlockState state, @Nullable Direction side) {
        return isPort(state) && side == state.getValue(FACING).getOpposite();
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        return position.relative(side, -state.getValue(PART_SIDE))
                .relative(facing, state.getValue(PART_REAR)).below(state.getValue(PART_Y));
    }

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        List<BlockPos> positions = new ArrayList<>(8);
        for (int y = 0; y <= 1; y++) for (int rear = 0; rear <= 1; rear++) for (int cross = 0; cross <= 1; cross++) {
            positions.add(core.relative(side, cross).relative(facing.getOpposite(), rear).above(y));
        }
        return positions;
    }

    public static List<Connection> connections(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        Direction outward = facing.getOpposite();
        return List.of(
                new Connection(core.relative(outward), core.relative(outward, 2), outward),
                new Connection(core.relative(outward).relative(side),
                        core.relative(outward, 2).relative(side), outward));
    }

    private BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        int cross = delta.getX() * side.getStepX() + delta.getZ() * side.getStepZ();
        Direction outward = facing.getOpposite();
        int rear = delta.getX() * outward.getStepX() + delta.getZ() * outward.getStepZ();
        return defaultBlockState().setValue(FACING, facing).setValue(PART_SIDE, cross)
                .setValue(PART_REAR, rear).setValue(PART_Y, delta.getY());
    }

    public record Connection(BlockPos port, BlockPos target, Direction outward) { }
}
