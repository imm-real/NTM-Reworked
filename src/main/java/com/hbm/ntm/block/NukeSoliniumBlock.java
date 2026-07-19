package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.NukeSoliniumBlockEntity;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonatable;
import com.hbm.ntm.nuclear.SoliniumCloudEntity;
import com.hbm.ntm.nuclear.SoliniumExplosionEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/** The Blue Rinse. Washes organics at extremely high temperature. */
public final class NukeSoliniumBlock extends BaseEntityBlock implements RemoteDetonatable {
    public static final MapCodec<NukeSoliniumBlock> CODEC = simpleCodec(NukeSoliniumBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public NukeSoliniumBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(pos) instanceof NukeSoliniumBlockEntity bomb) {
            bomb.setCustomName(stack.getHoverName());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof NukeSoliniumBlockEntity bomb) {
            serverPlayer.openMenu(bomb, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && level.hasNeighborSignal(pos)
                && level.getBlockEntity(pos) instanceof NukeSoliniumBlockEntity bomb && bomb.isReady()) {
            detonate((ServerLevel) level, pos, bomb);
        }
    }

    public boolean detonate(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof NukeSoliniumBlockEntity bomb) || !bomb.isReady()) return false;
        detonate(level, pos, bomb);
        return true;
    }

    @Override
    public DetonationResult detonateRemotely(ServerLevel level, BlockPos position) {
        return detonate(level, position)
                ? DetonationResult.DETONATED
                : DetonationResult.ERROR_MISSING_COMPONENT;
    }

    private void detonate(ServerLevel level, BlockPos pos, NukeSoliniumBlockEntity bomb) {
        bomb.clearForDetonation();
        level.removeBlock(pos, false);
        int radius = HbmConfig.SOLINIUM_RADIUS.get();
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        // Source igniteTestBomb: the blast is centered on the block (block + 0.5), the cloud sits
        // at the raw block coordinate, and the "random.explode" report uses volume 1.0F, pitch
        // rand * 0.1 + 0.9. The absent High Energy Field Jammer scan (statFacFleija) is a documented
        // permanent no-op, so the blast is always spawned.
        SoliniumExplosionEntity explosion = SoliniumExplosionEntity.create(level, x + 0.5D, y + 0.5D, z + 0.5D, radius);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                1.0F, level.random.nextFloat() * 0.1F + 0.9F);
        level.addFreshEntity(explosion);
        SoliniumCloudEntity cloud = SoliniumCloudEntity.create(level, x, y, z, radius);
        level.addFreshEntity(cloud);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof NukeSoliniumBlockEntity bomb
                && !bomb.detonating()) {
            bomb.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NukeSoliniumBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
