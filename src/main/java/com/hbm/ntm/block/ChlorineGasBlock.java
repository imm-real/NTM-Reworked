package com.hbm.ntm.block;

import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.hazard.HazardSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Translucent chlorine cloud from a powered seal and a failed safety meeting. */
public final class ChlorineGasBlock extends Block {
    static final int INITIAL_DELAY = 10;
    static final int MOVE_DELAY = 2;
    static final int DISSIPATION_DENOMINATOR = 10;
    private static ClientParticleSpawner clientParticleSpawner = (level, pos) -> { };

    public ChlorineGasBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                              CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                   CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        if (!level.isClientSide) level.scheduleTick(pos, this, INITIAL_DELAY);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        clientParticleSpawner.spawn(level, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(DISSIPATION_DENOMINATOR) == 0) {
            level.removeBlock(pos, false);
            return;
        }

        Direction first = random.nextInt(5) == 0 ? Direction.UP : Direction.DOWN;
        if (!tryMove(level, pos, first)
                && !tryMove(level, pos, Direction.Plane.HORIZONTAL.getRandomDirection(random))) {
            level.scheduleTick(pos, this, MOVE_DELAY);
        }
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living)) return;
        if (HazardSystem.hasProtection(living, HazardProtection.GAS_LUNG, 1)) return;

        living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 5 * 20, 0));
        living.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 20, 2));
        living.addEffect(new MobEffectInstance(MobEffects.WITHER, 20, 1));
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30 * 20, 1));
        living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 30 * 20, 2));
    }

    private boolean tryMove(ServerLevel level, BlockPos from, Direction direction) {
        BlockPos to = from.relative(direction);
        if (!level.getBlockState(to).isAir()) return false;
        level.removeBlock(from, false);
        level.setBlock(to, defaultBlockState(), Block.UPDATE_ALL);
        level.scheduleTick(to, this, MOVE_DELAY);
        return true;
    }

    /** Installed only by the client bootstrap, keeping client classes out of common block loading. */
    public static void setClientParticleSpawner(ClientParticleSpawner spawner) {
        clientParticleSpawner = spawner;
    }

    @FunctionalInterface
    public interface ClientParticleSpawner {
        void spawn(Level level, BlockPos pos);
    }
}
