package com.hbm.ntm.block;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Dead grass, glowing mycelium and frozen grass sharing one bad address. */
public final class WasteEarthBlock extends Block {
    /** Three flavors of ground nobody should lick. */
    public enum Variant { WASTE, MYCELIUM, FROZEN }

    private final Variant variant;

    public WasteEarthBlock(Properties properties, Variant variant) {
        super(properties);
        this.variant = variant;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (variant == Variant.MYCELIUM && HbmConfig.ENABLE_WASTE_MYCELIUM_SPREAD.get()) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos target = pos.offset(x, y, z);
                        BlockState targetState = level.getBlockState(target);
                        BlockState above = level.getBlockState(target.above());
                        if (!above.canOcclude() && (targetState.is(Blocks.DIRT)
                                || targetState.is(Blocks.GRASS_BLOCK)
                                || targetState.is(Blocks.MYCELIUM)
                                || targetState.is(ModBlocks.WASTE_EARTH.get()))) {
                            level.setBlock(target, defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                }
            }
        }

        // Waste cleans itself up. Frozen grass has opted out of random ticks.
        if (variant != Variant.FROZEN) {
            BlockPos abovePos = pos.above();
            BlockState above = level.getBlockState(abovePos);
            if (HbmConfig.CLEANUP_WASTE_EARTH.get()
                    || level.getMaxLocalRawBrightness(abovePos) < 4
                    && above.getLightBlock(level, abovePos) > 2) {
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity living) {
            if (variant == Variant.MYCELIUM) {
                LegacyRadiationBlockEffects.refresh(living, 3);
            } else if (variant == Variant.FROZEN) {
                // Two minutes of Slowness III for stepping on the crunchy grass.
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2 * 60 * 20, 2));
            }
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (variant == Variant.MYCELIUM) {
            level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + random.nextFloat(), pos.getY() + 1.1D,
                    pos.getZ() + random.nextFloat(), 0.0D, 0.0D, 0.0D);
        }
    }
}
