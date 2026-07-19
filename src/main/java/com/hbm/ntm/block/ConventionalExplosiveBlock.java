package com.hbm.ntm.block;

import com.hbm.ntm.entity.PrimedExplosiveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class ConventionalExplosiveBlock extends Block implements PrimedExplosiveBlock {
    public static final BooleanProperty UNSTABLE = BlockStateProperties.UNSTABLE;
    public static final int NORMAL_FUSE = 80;
    public static final int CHAIN_FUSE = 20;

    private final float blastPower;

    public ConventionalExplosiveBlock(Properties properties, float blastPower) {
        super(properties);
        this.blastPower = blastPower;
        registerDefaultState(defaultBlockState().setValue(UNSTABLE, false));
    }

    public float blastPower() {
        return blastPower;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        if (!oldState.is(this)) {
            if (level.hasNeighborSignal(pos)) {
                prime(level, pos, null, NORMAL_FUSE);
                level.removeBlock(pos, false);
            } else {
                checkAdjacentFire(level, pos);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   BlockPos neighborPos, boolean moving) {
        if (level.hasNeighborSignal(pos)) {
            prime(level, pos, null, NORMAL_FUSE);
            level.removeBlock(pos, false);
        } else {
            checkAdjacentFire(level, pos);
        }
    }

    private void checkAdjacentFire(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).is(Blocks.FIRE)) {
                prime(level, pos, null, NORMAL_FUSE);
                level.removeBlock(pos, false);
                return;
            }
        }
    }

    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction direction,
                             @Nullable LivingEntity igniter) {
        prime(level, pos, igniter, NORMAL_FUSE);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.FLINT_AND_STEEL)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }

        prime(level, pos, player, NORMAL_FUSE);
        level.removeBlock(pos, false);
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide && projectile instanceof AbstractArrow && projectile.isOnFire()
                && projectile.mayInteract(level, hit.getBlockPos())) {
            Entity owner = projectile.getOwner();
            prime(level, hit.getBlockPos(), owner instanceof LivingEntity living ? living : null, NORMAL_FUSE);
            level.removeBlock(hit.getBlockPos(), false);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && state.getValue(UNSTABLE)) {
            prime(level, pos, null, NORMAL_FUSE);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        int fuse = level.random.nextInt(CHAIN_FUSE) + CHAIN_FUSE / 2;
        Entity source = explosion.getIndirectSourceEntity();
        prime(level, pos, source instanceof LivingEntity living ? living : null, fuse, false);
    }

    private void prime(Level level, BlockPos pos, @Nullable LivingEntity owner, int fuse) {
        prime(level, pos, owner, fuse, true);
    }

    private void prime(Level level, BlockPos pos, @Nullable LivingEntity owner, int fuse, boolean sound) {
        if (level.isClientSide) {
            return;
        }
        PrimedExplosiveEntity entity = new PrimedExplosiveEntity(
                level,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                owner,
                defaultBlockState(),
                fuse
        );
        level.addFreshEntity(entity);
        if (sound) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TNT_PRIMED,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    public void detonatePrimed(ServerLevel level, double x, double y, double z, PrimedExplosiveEntity entity) {
        level.explode(entity, x, y, z, blastPower, true, Level.ExplosionInteraction.TNT);
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 100;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 15;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UNSTABLE);
    }
}
