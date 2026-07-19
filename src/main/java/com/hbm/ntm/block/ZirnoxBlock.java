package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.ZirnoxBlockEntity;
import com.hbm.ntm.blockentity.ZirnoxProxyBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Eighty-three blocks volunteering to be one ZIRNOX. */
public final class ZirnoxBlock extends BaseEntityBlock {
    public static final MapCodec<ZirnoxBlock> CODEC = simpleCodec(ZirnoxBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 82);
    private static final List<Offset> OFFSETS = buildOffsets();
    private static final int CORE = findPart(0, 0, 0);
    private static final VoxelShape FULL = box(0, 0, 0, 16, 16, 16);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    public ZirnoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, CORE));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos core = context.getClickedPos();
        for (int i = 0; i < OFFSETS.size(); i++) {
            BlockPos part = position(core, facing, OFFSETS.get(i));
            if (!part.equals(core) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, CORE);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int i = 0; i < OFFSETS.size(); i++) {
            BlockPos part = position(pos, facing, OFFSETS.get(i));
            level.setBlock(part, defaultBlockState().setValue(FACING, facing).setValue(PART, i), Block.UPDATE_ALL);
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) { return FULL; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos core = corePosition(pos, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof ZirnoxBlockEntity reactor) {
            serverPlayer.openMenu(reactor, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos,
                                      BlockState next, boolean moved) {
        if (state.is(next.getBlock())) { super.onRemove(state, level, pos, next, moved); return; }
        BlockPos core = corePosition(pos, state);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                if (level.getBlockEntity(core) instanceof ZirnoxBlockEntity reactor) {
                    Containers.dropContents(level, core, reactor);
                }
                Direction facing = state.getValue(FACING);
                for (Offset offset : OFFSETS) {
                    BlockPos part = position(core, facing, offset);
                    if (!part.equals(pos) && level.getBlockState(part).is(this)) {
                        level.setBlock(part, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            } finally { REMOVING.set(false); }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) == null
                ? List.of(new ItemStack(ModItems.REACTOR_ZIRNOX_ITEM.get())) : List.of();
    }
    @Override public boolean dropFromExplosion(Explosion explosion) { return false; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isCore(state) ? new ZirnoxBlockEntity(pos, state)
                : isPort(state) ? new ZirnoxProxyBlockEntity(pos, state) : null;
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.REACTOR_ZIRNOX.get(),
                ZirnoxBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public static boolean isCore(BlockState state) { return state.getValue(PART) == CORE; }
    public static boolean isPort(BlockState state) {
        Offset o = OFFSETS.get(state.getValue(PART));
        return Math.abs(o.side) == 2 && (o.y == 1 || o.y == 3) && o.forward == 0;
    }
    public static boolean canConnectAt(BlockState state, @Nullable Direction side) {
        if (!isPort(state) || side == null) return false;
        Offset o = OFFSETS.get(state.getValue(PART));
        Direction outward = o.side > 0 ? state.getValue(FACING).getClockWise()
                : state.getValue(FACING).getCounterClockWise();
        return side == outward;
    }
    public static BlockPos corePosition(BlockPos pos, BlockState state) {
        Offset o = OFFSETS.get(state.getValue(PART));
        Direction side = state.getValue(FACING).getClockWise();
        Direction forward = state.getValue(FACING).getOpposite();
        return pos.relative(side, -o.side).relative(forward, -o.forward).below(o.y);
    }
    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        return OFFSETS.stream().map(o -> position(core, facing, o)).toList();
    }
    public static List<Connection> connections(BlockPos core, Direction facing) {
        List<Connection> result = new ArrayList<>(4);
        Direction side = facing.getClockWise();
        for (int sign : new int[]{-1, 1}) for (int y : new int[]{1, 3}) {
            Direction outward = sign > 0 ? side : side.getOpposite();
            BlockPos port = core.relative(side, sign * 2).above(y);
            result.add(new Connection(port, port.relative(outward), outward));
        }
        return result;
    }

    private static BlockPos position(BlockPos core, Direction facing, Offset o) {
        return core.relative(facing.getClockWise(), o.side)
                .relative(facing.getOpposite(), o.forward).above(o.y);
    }
    private static int findPart(int side, int y, int forward) {
        for (int i = 0; i < OFFSETS.size(); i++) if (OFFSETS.get(i).equals(new Offset(side, y, forward))) return i;
        throw new IllegalStateException("Missing ZIRNOX core");
    }
    private static List<Offset> buildOffsets() {
        List<Offset> offsets = new ArrayList<>(83);
        for (int y = 0; y <= 1; y++) for (int forward = -2; forward <= 2; forward++)
            for (int side = -2; side <= 2; side++) offsets.add(new Offset(side, y, forward));
        for (int y = 2; y <= 4; y++) {
            for (int forward = -1; forward <= 1; forward++) for (int side = -1; side <= 1; side++)
                offsets.add(new Offset(side, y, forward));
            offsets.add(new Offset(-2, y, 0));
            offsets.add(new Offset(2, y, 0));
        }
        if (offsets.size() != 83) throw new IllegalStateException("Bad ZIRNOX volume: " + offsets.size());
        return List.copyOf(offsets);
    }

    private record Offset(int side, int y, int forward) { }
    public record Connection(BlockPos port, BlockPos target, Direction outward) { }
}
