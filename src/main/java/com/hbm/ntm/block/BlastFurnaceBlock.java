package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.BlastFurnaceBlockEntity;
import com.hbm.ntm.blockentity.BlastFurnaceProxyBlockEntity;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A 3x7x3 blast furnace with seven sanctioned poking holes. */
public final class BlastFurnaceBlock extends BaseEntityBlock {
    public static final MapCodec<BlastFurnaceBlock> CODEC = simpleCodec(BlastFurnaceBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty Y = IntegerProperty.create("part_y", 0, 6);
    public static final IntegerProperty Z = IntegerProperty.create("part_z", 0, 2);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = Shapes.block();

    public BlastFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(X, 1).setValue(Y, 0).setValue(Z, 1));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite());
        for (BlockPos part : partPositions(core)) {
            if (!part.equals(clicked) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(clicked, core, facing);
    }

    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state,
                                      @Nullable LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core)) {
            level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        }
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(core) instanceof BlastFurnaceBlockEntity furnace) {
            furnace.setCustomName(stack.getHoverName());
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) { return FULL; }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                         BlockPos position, Player player, InteractionHand hand,
                                                         BlockHitResult hit) {
        BlockPos core = corePosition(position, state);
        if (!(level.getBlockEntity(core) instanceof BlastFurnaceBlockEntity furnace)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof UniversalFluidTankItem
                && UniversalFluidTankItem.fluid(stack) == UniversalFluidTankItem.ContainedFluid.AIRBLAST) {
            if (furnace.airTank().fill(new FluidStack(com.hbm.ntm.registry.ModFluids.AIRBLAST.get(), 1_000),
                    IFluidHandler.FluidAction.SIMULATE) < 1_000) return ItemInteractionResult.FAIL;
            if (!level.isClientSide) {
                furnace.airTank().fill(new FluidStack(com.hbm.ntm.registry.ModFluids.AIRBLAST.get(), 1_000),
                        IFluidHandler.FluidAction.EXECUTE);
                replaceContainer(player, hand, stack, new ItemStack(ModItems.FLUID_TANK_EMPTY.get()));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(ModItems.FLUID_TANK_EMPTY.get()) && furnace.flueTank().getFluidAmount() >= 1_000) {
            if (!level.isClientSide) {
                furnace.flueTank().drain(1_000, IFluidHandler.FluidAction.EXECUTE);
                replaceContainer(player, hand, stack, UniversalFluidTankItem.create(
                        ModItems.FLUID_TANK_FULL.get(), UniversalFluidTankItem.ContainedFluid.FLUE, 1));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static void replaceContainer(Player player, InteractionHand hand, ItemStack input, ItemStack output) {
        if (player.getAbilities().instabuild) return;
        input.shrink(1);
        if (input.isEmpty()) player.setItemInHand(hand, output);
        else if (!player.getInventory().add(output)) player.drop(output, false);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                          Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof BlastFurnaceBlockEntity furnace) {
            serverPlayer.openMenu(furnace, buffer -> buffer.writeBlockPos(core));
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
        REMOVING.set(true);
        try {
            if (!level.isClientSide && level.getBlockEntity(core) instanceof BlastFurnaceBlockEntity furnace) {
                Containers.dropContents(level, core, furnace);
                furnace.clearContent();
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

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        if (isCore(state)) return new BlastFurnaceBlockEntity(position, state);
        return isPort(state) ? new BlastFurnaceProxyBlockEntity(position, state) : null;
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_BLAST_FURNACE.get(),
                BlastFurnaceBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Y, Z);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(X) == 1 && state.getValue(Y) == 0 && state.getValue(Z) == 1;
    }

    public static boolean isPort(BlockState state) {
        int x = state.getValue(X);
        int y = state.getValue(Y);
        int z = state.getValue(Z);
        return y == 0 && (x == 1 ^ z == 1)
                || x == 1 && z == 2 && (y == 3 || y == 5)
                || x == 1 && z == 1 && y == 6;
    }

    @Nullable public static Direction portSide(BlockState state) {
        if (!isPort(state)) return null;
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        int x = state.getValue(X);
        int y = state.getValue(Y);
        int z = state.getValue(Z);
        if (y == 6) return Direction.UP;
        if (z == 0) return facing.getOpposite();
        if (z == 2) return facing;
        if (x == 0) return side.getOpposite();
        if (x == 2) return side;
        return null;
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        return position.relative(side, 1 - state.getValue(X))
                .relative(facing, 1 - state.getValue(Z)).below(state.getValue(Y));
    }

    public static List<BlockPos> partPositions(BlockPos core) {
        List<BlockPos> result = new ArrayList<>(63);
        for (int y = 0; y <= 6; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            result.add(core.offset(x, y, z));
        }
        return result;
    }

    public BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        int x = delta.getX() * side.getStepX() + delta.getZ() * side.getStepZ() + 1;
        int z = delta.getX() * facing.getStepX() + delta.getZ() * facing.getStepZ() + 1;
        return defaultBlockState().setValue(FACING, facing).setValue(X, x)
                .setValue(Y, delta.getY()).setValue(Z, z);
    }
}
