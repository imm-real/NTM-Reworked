package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.DfcEmitterBlockEntity;
import com.hbm.ntm.blockentity.DfcInjectorBlockEntity;
import com.hbm.ntm.blockentity.DfcReceiverBlockEntity;
import com.hbm.ntm.blockentity.DfcStabilizerBlockEntity;
import com.hbm.ntm.dfc.DfcKind;
import com.hbm.ntm.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Six-way DFC shell. Its inventory stays behind when the block does not. */
public final class DfcComponentBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private final DfcKind kind;
    private final MapCodec<DfcComponentBlock> codec;

    public DfcComponentBlock(Properties properties, DfcKind kind) {
        super(properties);
        if (kind == DfcKind.CORE) throw new IllegalArgumentException("DFC core has its own block");
        this.kind = kind;
        this.codec = MapCodec.unit(this);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public DfcKind kind() { return kind; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof net.minecraft.world.MenuProvider provider) {
            serverPlayer.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (kind) {
            case EMITTER -> new DfcEmitterBlockEntity(pos, state);
            case INJECTOR -> new DfcInjectorBlockEntity(pos, state);
            case RECEIVER -> new DfcReceiverBlockEntity(pos, state);
            case STABILIZER -> new DfcStabilizerBlockEntity(pos, state);
            default -> null;
        };
    }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return switch (kind) {
            case EMITTER -> createTickerHelper(type, ModBlockEntities.DFC_EMITTER.get(), DfcEmitterBlockEntity::tick);
            case INJECTOR -> createTickerHelper(type, ModBlockEntities.DFC_INJECTOR.get(), DfcInjectorBlockEntity::tick);
            case RECEIVER -> createTickerHelper(type, ModBlockEntities.DFC_RECEIVER.get(), DfcReceiverBlockEntity::tick);
            case STABILIZER -> createTickerHelper(type, ModBlockEntities.DFC_STABILIZER.get(), DfcStabilizerBlockEntity::tick);
            default -> null;
        };
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
