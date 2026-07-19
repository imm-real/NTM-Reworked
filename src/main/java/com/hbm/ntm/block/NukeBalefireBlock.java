package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.NukeBalefireBlockEntity;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonatable;
import com.hbm.ntm.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Source {@code com.hbm.blocks.bomb.NukeBalefire} (implements {@code IBomb}). The "Balefire Bomb"
 * ({@code hbm:nuke_fstbmb}); "fstbmb" is the legacy internal name shared by its block entity, menu,
 * GUI, renderer, and sounds. Modeled on the placed-nuke skeleton: INVISIBLE (TESR-only), redstone and
 * remote detonation gated on the loaded egg + battery, and a menu opened by clicking without an item.
 */
public final class NukeBalefireBlock extends BaseEntityBlock implements RemoteDetonatable {
    public static final MapCodec<NukeBalefireBlock> CODEC = simpleCodec(NukeBalefireBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public NukeBalefireBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(pos) instanceof NukeBalefireBlockEntity bomb) {
            bomb.setCustomName(stack.getHoverName());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof NukeBalefireBlockEntity bomb) {
            serverPlayer.openMenu(bomb, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Source onNeighborBlockChange: if indirectly powered -> explode() (which no-ops unless loaded).
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && level.hasNeighborSignal(pos)
                && level.getBlockEntity(pos) instanceof NukeBalefireBlockEntity bomb && bomb.isLoaded()) {
            bomb.explode();
        }
    }

    public boolean detonate(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof NukeBalefireBlockEntity bomb) || !bomb.isLoaded()) return false;
        bomb.explode();
        return true;
    }

    // Source explode(world, x, y, z): DETONATED when loaded, else ERROR_MISSING_COMPONENT.
    @Override
    public DetonationResult detonateRemotely(ServerLevel level, BlockPos position) {
        return detonate(level, position)
                ? DetonationResult.DETONATED
                : DetonationResult.ERROR_MISSING_COMPONENT;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof NukeBalefireBlockEntity bomb
                && !bomb.detonating()) {
            bomb.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NukeBalefireBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.NUKE_FSTBMB.get(), NukeBalefireBlockEntity::tick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
