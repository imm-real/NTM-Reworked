package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.ConveyorBoxerBlockEntity;
import com.hbm.ntm.conveyor.ConveyorEnterable;
import com.hbm.ntm.entity.MovingConveyorItemEntity;
import com.hbm.ntm.entity.MovingConveyorPackageEntity;
import com.hbm.ntm.item.ConveyorWandItem;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Conveyor package maker registered as {@code crane_boxer}. */
public final class ConveyorBoxerBlock extends BaseEntityBlock implements ConveyorEnterable {
    public static final MapCodec<ConveyorBoxerBlock> CODEC = simpleCodec(ConveyorBoxerBlock::new);
    public static final DirectionProperty INPUT = DirectionProperty.create("input");
    public static final DirectionProperty OUTPUT = DirectionProperty.create("output");

    public ConveyorBoxerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(INPUT, Direction.NORTH)
                .setValue(OUTPUT, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction input = context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(INPUT, input).setValue(OUTPUT, input.getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(pos) instanceof ConveyorBoxerBlockEntity boxer) {
            boxer.setCustomName(stack.getHoverName());
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof ConveyorWandItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(ModItems.SCREWDRIVER.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            BlockState changed = player.isShiftKeyDown()
                    ? setOutput(state, hit.getDirection())
                    : setInput(state, hit.getDirection());
            level.setBlock(pos, changed, Block.UPDATE_ALL);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Preserves TileEntityCraneBase.setInput, including double-click reversal and side swapping. */
    public static BlockState setInput(BlockState state, Direction requested) {
        Direction oldInput = state.getValue(INPUT);
        Direction oldOutput = state.getValue(OUTPUT);
        Direction input = requested == oldInput ? requested.getOpposite() : requested;
        return input == oldOutput
                ? state.setValue(INPUT, input).setValue(OUTPUT, oldInput)
                : state.setValue(INPUT, input);
    }

    /** Preserves TileEntityCraneBase.setOutputOverride, including double-click reversal and swapping. */
    public static BlockState setOutput(BlockState state, Direction requested) {
        Direction oldInput = state.getValue(INPUT);
        Direction oldOutput = state.getValue(OUTPUT);
        Direction output = requested == oldOutput ? requested.getOpposite() : requested;
        return output == oldInput
                ? state.setValue(INPUT, oldOutput).setValue(OUTPUT, oldInput)
                : state.setValue(OUTPUT, output);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ConveyorBoxerBlockEntity boxer) {
            serverPlayer.openMenu(boxer, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof ConveyorBoxerBlockEntity boxer) {
            Containers.dropContents(level, pos, boxer);
            boxer.clearContent();
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ConveyorBoxerBlockEntity boxer
                ? boxer.comparatorOutput() : 0;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConveyorBoxerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.CRANE_BOXER.get(),
                ConveyorBoxerBlockEntity::tick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(INPUT, OUTPUT);
    }

    @Override
    public boolean canConveyorItemEnter(Level level, BlockPos pos, Direction side,
                                        MovingConveyorItemEntity item) {
        return level.getBlockState(pos).getValue(INPUT) == side;
    }

    @Override
    public void onConveyorItemEnter(Level level, BlockPos pos, Direction side,
                                    MovingConveyorItemEntity item) {
        if (level.getBlockEntity(pos) instanceof ConveyorBoxerBlockEntity boxer) {
            boxer.insertOrDrop(item.getItemStack());
        }
    }

    @Override
    public boolean canConveyorPackageEnter(Level level, BlockPos pos, Direction side,
                                           MovingConveyorPackageEntity conveyorPackage) {
        return true;
    }

    @Override
    public void onConveyorPackageEnter(Level level, BlockPos pos, Direction side,
                                       MovingConveyorPackageEntity conveyorPackage) {
        if (level.getBlockEntity(pos) instanceof ConveyorBoxerBlockEntity boxer) {
            for (ItemStack stack : conveyorPackage.getItemStacks()) boxer.insertOrDrop(stack);
        }
    }
}
