package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.FluidUtilityBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class FluidUtilityBlock extends BaseEntityBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.ENABLED;
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private final MapCodec<FluidUtilityBlock> codec = MapCodec.unit(this);
    private final Kind kind;

    public FluidUtilityBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any()
                .setValue(FluidDuctBlock.TYPE, FluidIdentifierItem.Selection.NONE)
                .setValue(OPEN, false)
                .setValue(FACING, Direction.DOWN));
    }

    public Kind kind() {
        return kind;
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        if (kind == Kind.GAUGE) {
            state = state.setValue(FACING, context.getNearestLookingDirection().getOpposite());
        }
        if (kind == Kind.SWITCH) {
            state = state.setValue(OPEN, context.getLevel().hasNeighborSignal(context.getClickedPos()));
        }
        return state;
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                        BlockPos pos, Player player, InteractionHand hand,
                                                        BlockHitResult hit) {
        if (stack.getItem() instanceof FluidIdentifierItem) {
            FluidIdentifierItem.Selection selection = FluidIdentifierItem.primary(stack);
            if (selection == FluidIdentifierItem.Selection.NONE) return ItemInteractionResult.FAIL;
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    com.hbm.ntm.registry.ModBlocks.FLUID_DUCT_NEO.get()
                            .retagConnected(level, pos, state.getValue(FluidDuctBlock.TYPE), selection, 64);
                } else {
                    level.setBlock(pos, state.setValue(FluidDuctBlock.TYPE, selection), Block.UPDATE_ALL);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (kind != Kind.VALVE || player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) setOpen(level, pos, state, !state.getValue(OPEN), true);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                             BlockPos neighborPos, boolean movedByPiston) {
        if (kind == Kind.SWITCH && !level.isClientSide) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(OPEN)) setOpen(level, pos, state, powered, true);
        }
        super.neighborChanged(state, level, pos, block, neighborPos, movedByPiston);
    }

    public static void setOpen(Level level, BlockPos pos, BlockState state, boolean open, boolean sourcePitch) {
        if (state.getValue(OPEN) == open) return;
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, pos, ModSounds.REACTOR_START.get(), SoundSource.BLOCKS,
                1.0F, sourcePitch && !open ? 0.85F : 1.0F);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidUtilityBlockEntity(pos, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.FLUID_UTILITY.get(), FluidUtilityBlockEntity::serverTick);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FluidDuctBlock.TYPE, OPEN, FACING);
    }

    public enum Kind {
        VALVE, SWITCH, COUNTER, GAUGE
    }
}
