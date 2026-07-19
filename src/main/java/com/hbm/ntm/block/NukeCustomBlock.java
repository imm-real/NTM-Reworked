package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.NukeCustomBlockEntity;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonatable;
import com.hbm.ntm.nuclear.CustomNukeExplosion;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** This entire class sucks ass, now remotely detonatable. */
public final class NukeCustomBlock extends BaseEntityBlock implements RemoteDetonatable {
    public static final MapCodec<NukeCustomBlock> CODEC = simpleCodec(NukeCustomBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public NukeCustomBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Store player direction, let the renderer do the interpretive dance.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(pos) instanceof NukeCustomBlockEntity bomb) {
            bomb.setCustomName(stack.getHoverName());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof NukeCustomBlockEntity bomb) {
            serverPlayer.openMenu(bomb, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   BlockPos neighborPos, boolean movedByPiston) {
        // Redstone in, neighborhood out.
        if (!level.isClientSide && level.hasNeighborSignal(pos)) {
            detonate((ServerLevel) level, pos);
        }
    }

    /** Returns false when somebody stole the block entity. */
    public boolean detonate(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof NukeCustomBlockEntity bomb)) return false;
        // No custom_fall yet, so it dies where it stands.
        CustomNukeExplosion.Yields y = bomb.yields();
        bomb.clearForDetonation();
        level.removeBlock(pos, false);
        // Yes, explodeCustom adds another 0.5. This entire class sucks ass, remember?
        CustomNukeExplosion.explodeCustom(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                y.tnt(), y.nuke(), y.hydro(), y.amat(), y.dirty(), y.schrab(), y.euph());
        return true;
    }

    @Override
    public DetonationResult detonateRemotely(ServerLevel level, BlockPos position) {
        // No tile, no boom, no paperwork.
        return detonate(level, position) ? DetonationResult.DETONATED : DetonationResult.UNDEFINED;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof NukeCustomBlockEntity bomb
                && !bomb.detonating()) {
            bomb.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NukeCustomBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
