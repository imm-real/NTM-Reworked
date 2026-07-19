package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.MachinePressBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class MachinePressBlock extends BaseEntityBlock {
    public static final MapCodec<MachinePressBlock> CODEC = simpleCodec(MachinePressBlock::new);
    public static final EnumProperty<PressPart> PART = EnumProperty.create("part", PressPart.class);
    private static final ThreadLocal<Boolean> REMOVING_STRUCTURE = ThreadLocal.withInitial(() -> false);

    public MachinePressBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, PressPart.LOWER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos position = context.getClickedPos();
        if (!context.getLevel().getBlockState(position.above()).canBeReplaced(context)
                || !context.getLevel().getBlockState(position.above(2)).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        level.setBlock(position.above(), defaultBlockState().setValue(PART, PressPart.MIDDLE), Block.UPDATE_ALL);
        level.setBlock(position.above(2), defaultBlockState().setValue(PART, PressPart.UPPER), Block.UPDATE_ALL);
        if (level.getBlockEntity(position) instanceof MachinePressBlockEntity press && stack.has(DataComponents.CUSTOM_NAME)) {
            press.setCustomName(stack.getHoverName());
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            BlockHitResult hitResult
    ) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos lowerPosition = lowerPosition(position, state.getValue(PART));
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(lowerPosition) instanceof MachinePressBlockEntity press) {
            serverPlayer.openMenu(press, buffer -> buffer.writeBlockPos(lowerPosition));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, position, newState, movedByPiston);
            return;
        }

        if (!level.isClientSide && state.getValue(PART) == PressPart.LOWER
                && level.getBlockEntity(position) instanceof MachinePressBlockEntity press) {
            Containers.dropContents(level, position, press);
        }

        if (!REMOVING_STRUCTURE.get()) {
            REMOVING_STRUCTURE.set(true);
            try {
                BlockPos lowerPosition = lowerPosition(position, state.getValue(PART));
                for (int offset = 0; offset < 3; offset++) {
                    BlockPos partPosition = lowerPosition.above(offset);
                    if (!partPosition.equals(position) && level.getBlockState(partPosition).is(this)) {
                        level.setBlock(partPosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            } finally {
                REMOVING_STRUCTURE.set(false);
            }
        }
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return state.getValue(PART) == PressPart.LOWER ? new MachinePressBlockEntity(position, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (state.getValue(PART) != PressPart.LOWER) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.MACHINE_PRESS.get(), MachinePressBlockEntity::tick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    public static BlockPos lowerPosition(BlockPos position, PressPart part) {
        return position.below(part.offset());
    }

    public enum PressPart implements StringRepresentable {
        LOWER("lower", 0),
        MIDDLE("middle", 1),
        UPPER("upper", 2);

        private final String serializedName;
        private final int offset;

        PressPart(String serializedName, int offset) {
            this.serializedName = serializedName;
            this.offset = offset;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public int offset() {
            return offset;
        }
    }
}
