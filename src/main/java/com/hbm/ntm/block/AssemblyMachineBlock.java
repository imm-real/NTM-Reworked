package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.AssemblyMachineBlockEntity;
import com.hbm.ntm.blockentity.AssemblyMachineProxyBlockEntity;
import com.hbm.ntm.energy.HeNetworkManager;
import com.hbm.ntm.network.AssemblyRecipeSyncPayload;
import com.hbm.ntm.recipe.AssemblyRecipes;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AssemblyMachineBlock extends BaseEntityBlock {
    public static final MapCodec<AssemblyMachineBlock> CODEC = simpleCodec(AssemblyMachineBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty Z = IntegerProperty.create("part_z", 0, 2);
    public static final IntegerProperty Y = IntegerProperty.create("part_y", 0, 2);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = box(0, 0, 0, 16, 16, 16);

    public AssemblyMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(X, 1).setValue(Z, 1).setValue(Y, 0));
    }

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
    public void setPlacedBy(Level level, BlockPos position, BlockState state, LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core)) level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(core) instanceof AssemblyMachineBlockEntity assembler) {
            assembler.setCustomName(stack.getHoverName());
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FULL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                               Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof AssemblyMachineBlockEntity assembler) {
            PacketDistributor.sendToPlayer(serverPlayer, AssemblyRecipeSyncPayload.from(AssemblyRecipes.all()));
            serverPlayer.openMenu(assembler, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean moved) {
        if (state.is(newState.getBlock())) { super.onRemove(state, level, position, newState, moved); return; }
        BlockPos core = corePosition(position, state);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                if (!level.isClientSide && level.getBlockEntity(core) instanceof AssemblyMachineBlockEntity assembler) {
                    Containers.dropContents(level, core, assembler);
                    assembler.clearContent();
                    if (level instanceof ServerLevel serverLevel) HeNetworkManager.get(serverLevel).destroyNode(core);
                }
                for (BlockPos part : partPositions(core)) {
                    if (!part.equals(position) && level.getBlockState(part).is(this)) {
                        level.setBlock(part, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            } finally { REMOVING.set(false); }
        }
        super.onRemove(state, level, position, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (isCore(state)) return new AssemblyMachineBlockEntity(pos, state);
        return state.getValue(Y) == 0 ? new AssemblyMachineProxyBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_ASSEMBLY_MACHINE.get(),
                AssemblyMachineBlockEntity::tick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Z, Y);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(X) == 1 && state.getValue(Z) == 1 && state.getValue(Y) == 0;
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        return position.offset(1 - state.getValue(X), -state.getValue(Y), 1 - state.getValue(Z));
    }

    public static List<BlockPos> partPositions(BlockPos core) {
        List<BlockPos> positions = new ArrayList<>(11);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) positions.add(core.offset(x, 0, z));
        positions.add(core.above());
        positions.add(core.above(2));
        return positions;
    }

    public static boolean isBottomProxy(BlockState state) { return state.getValue(Y) == 0 && !isCore(state); }

    public static boolean canConnectAt(BlockState state, Direction side) {
        if (side == null || !side.getAxis().isHorizontal() || !isBottomProxy(state)) return false;
        int x = state.getValue(X) - 1;
        int z = state.getValue(Z) - 1;
        return x < 0 && side == Direction.WEST || x > 0 && side == Direction.EAST
                || z < 0 && side == Direction.NORTH || z > 0 && side == Direction.SOUTH;
    }

    public BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        int y = part.getY() - core.getY();
        int x = y == 0 ? part.getX() - core.getX() + 1 : 1;
        int z = y == 0 ? part.getZ() - core.getZ() + 1 : 1;
        return defaultBlockState().setValue(FACING, facing).setValue(X, x).setValue(Z, z).setValue(Y, y);
    }
}
