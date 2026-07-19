package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.LargeNukeBlockEntity;
import com.hbm.ntm.nuclear.NuclearExplosionEntity;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonatable;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class LargeNukeBlock extends BaseEntityBlock implements RemoteDetonatable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final LargeNukeType type;
    private final MapCodec<LargeNukeBlock> codec;

    public LargeNukeBlock(Properties properties, LargeNukeType type) {
        super(properties);
        this.type = type;
        this.codec = MapCodec.unit(this);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    public LargeNukeType type() { return type; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }
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
                && level.getBlockEntity(pos) instanceof LargeNukeBlockEntity bomb) {
            bomb.setCustomName(stack.getHoverName());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof LargeNukeBlockEntity bomb) {
            serverPlayer.openMenu(bomb, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && level.hasNeighborSignal(pos)
                && level.getBlockEntity(pos) instanceof LargeNukeBlockEntity bomb && bomb.isReady()) {
            detonate((ServerLevel) level, pos, bomb);
        }
    }

    public boolean detonate(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof LargeNukeBlockEntity bomb) || !bomb.isReady()) return false;
        detonate(level, pos, bomb);
        return true;
    }

    @Override
    public DetonationResult detonateRemotely(ServerLevel level, BlockPos position) {
        return detonate(level, position)
                ? DetonationResult.DETONATED
                : DetonationResult.ERROR_MISSING_COMPONENT;
    }

    private void detonate(ServerLevel level, BlockPos pos, LargeNukeBlockEntity bomb) {
        int radius = bomb.detonationRadius();
        LargeNukeType bombType = bomb.type();
        bomb.clearForDetonation();
        level.removeBlock(pos, false);
        NuclearExplosionEntity.spawnLargeNuke(level, pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D, radius, bombType == LargeNukeType.BOY);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LargeNukeBlockEntity bomb
                && !bomb.detonating()) {
            bomb.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LargeNukeBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
