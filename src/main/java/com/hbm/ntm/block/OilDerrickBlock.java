package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.OilDerrickBlockEntity;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Oil derrick core followed everywhere by eighty-six dummy blocks. */
public final class OilDerrickBlock extends BaseEntityBlock {
    public static final String POWER = "power";
    public static final String OIL = "oil";
    public static final String GAS = "gas";

    public static final MapCodec<OilDerrickBlock> CODEC = simpleCodec(OilDerrickBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty Y = IntegerProperty.create("part_y", 0, 9);
    public static final IntegerProperty Z = IntegerProperty.create("part_z", 0, 2);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = Shapes.block();

    public OilDerrickBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(X, 1).setValue(Y, 0).setValue(Z, 1));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos core = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (BlockPos part : partPositions(core)) {
            if (!part.equals(core) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(core, core, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core)) {
            level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(core) instanceof OilDerrickBlockEntity derrick) {
            if (stack.has(DataComponents.CUSTOM_NAME)) derrick.setCustomName(stack.getHoverName());
            derrick.restoreFromItem(stack);
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
        if (player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof OilDerrickBlockEntity derrick) {
            serverPlayer.openMenu(derrick, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean moved) {
        if (state.is(newState.getBlock()) || REMOVING.get()) {
            super.onRemove(state, level, position, newState, moved);
            return;
        }
        BlockPos core = corePosition(position, state);
        REMOVING.set(true);
        try {
            if (!level.isClientSide && level.getBlockEntity(core) instanceof OilDerrickBlockEntity derrick) {
                Containers.dropContents(level, core, derrick);
                derrick.clearContent();
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
        super.onRemove(state, level, position, newState, moved);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        OilDerrickBlockEntity derrick = findDerrick(state, params);
        if (params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null) {
            if (derrick != null) derrick.detonateStoredFluids();
            return List.of();
        }
        return List.of(derrick == null ? new ItemStack(ModItems.MACHINE_WELL_ITEM.get()) : derrick.machineDrop());
    }

    @Nullable
    private static OilDerrickBlockEntity findDerrick(BlockState state, LootParams.Builder params) {
        BlockEntity supplied = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (supplied instanceof OilDerrickBlockEntity derrick) return derrick;
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) return null;
        BlockPos broken = BlockPos.containing(origin);
        BlockPos core = corePosition(broken, state);
        return params.getLevel().getBlockEntity(core) instanceof OilDerrickBlockEntity derrick ? derrick : null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isCore(state) ? new OilDerrickBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_WELL.get(),
                OilDerrickBlockEntity::tick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Y, Z);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(X) == 1 && state.getValue(Y) == 0 && state.getValue(Z) == 1;
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        return position.offset(1 - state.getValue(X), -state.getValue(Y), 1 - state.getValue(Z));
    }

    public static List<BlockPos> partPositions(BlockPos core) {
        List<BlockPos> positions = new ArrayList<>(86);
        positions.add(core);
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) positions.add(core.offset(x, 0, z));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            positions.add(core.offset(x, 1, z));
        }
        for (int y = 2; y <= 9; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            positions.add(core.offset(x, y, z));
        }
        return positions;
    }

    public BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        return defaultBlockState().setValue(FACING, facing)
                .setValue(X, part.getX() - core.getX() + 1)
                .setValue(Y, part.getY() - core.getY())
                .setValue(Z, part.getZ() - core.getZ() + 1);
    }
}
