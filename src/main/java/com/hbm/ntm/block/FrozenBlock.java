package com.hbm.ntm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Frozen dirt slows feet. Both blocks contain a suspiciously retrievable snowball. */
public final class FrozenBlock extends LegacyOreBlock {
    private final boolean slowdownOnWalk;

    public FrozenBlock(Properties properties, boolean slowdownOnWalk) {
        super(properties, Drop.vanilla("snowball", 1, 1, false));
        this.slowdownOnWalk = slowdownOnWalk;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && slowdownOnWalk && entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2 * 60 * 20, 2));
        }
        super.stepOn(level, pos, state, entity);
    }
}
