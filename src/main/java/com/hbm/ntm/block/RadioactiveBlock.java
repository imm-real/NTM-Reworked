package com.hbm.ntm.block;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.radiation.ChunkRadiationData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class RadioactiveBlock extends Block {
    private static final int EMISSION_INTERVAL = 20;
    private final float emissionPerSecond;
    private final boolean radiationFog;
    private final boolean schrabidiumFog;

    public RadioactiveBlock(BlockBehaviour.Properties properties, float emissionPerSecond) {
        this(properties, emissionPerSecond, false);
    }

    public RadioactiveBlock(BlockBehaviour.Properties properties, float emissionPerSecond, boolean radiationFog) {
        this(properties, emissionPerSecond, radiationFog, false);
    }

    public RadioactiveBlock(BlockBehaviour.Properties properties, float emissionPerSecond,
                            boolean radiationFog, boolean schrabidiumFog) {
        super(properties);
        this.emissionPerSecond = emissionPerSecond;
        this.radiationFog = radiationFog;
        this.schrabidiumFog = schrabidiumFog;
    }

    public boolean radiationFog() { return radiationFog; }
    public boolean schrabidiumFog() { return schrabidiumFog; }

    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, EMISSION_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (emissionPerSecond > 0 && HbmConfig.ENABLE_CHUNK_RADIATION.get()) {
            ChunkRadiationData.get(level).increment(pos, emissionPerSecond);
        }
        level.scheduleTick(pos, this, EMISSION_INTERVAL);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!radiationFog && !schrabidiumFog) return;
        ParticleOptions particle = schrabidiumFog
                ? ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.0F, 1.0F, 1.0F)
                : ParticleTypes.HAPPY_VILLAGER;
        for (Direction direction : Direction.values()) {
            if (!level.getBlockState(pos.relative(direction)).isAir()) continue;
            double x = pos.getX() + 0.5D + direction.getStepX() + random.nextDouble() * 3.0D - 1.5D;
            double y = pos.getY() + 0.5D + direction.getStepY() + random.nextDouble() * 3.0D - 1.5D;
            double z = pos.getZ() + 0.5D + direction.getStepZ() + random.nextDouble() * 3.0D - 1.5D;
            if (direction.getStepX() != 0) x = pos.getX() + 0.5D + direction.getStepX() * 0.5D
                    + random.nextDouble() * direction.getStepX();
            if (direction.getStepY() != 0) y = pos.getY() + 0.5D + direction.getStepY() * 0.5D
                    + random.nextDouble() * direction.getStepY();
            if (direction.getStepZ() != 0) z = pos.getZ() + 0.5D + direction.getStepZ() * 0.5D
                    + random.nextDouble() * direction.getStepZ();
            level.addParticle(particle, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }
}
