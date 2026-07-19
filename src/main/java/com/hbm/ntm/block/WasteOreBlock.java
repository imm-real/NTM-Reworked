package com.hbm.ntm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Source trinitite sand and charred-plank drop rules. */
public final class WasteOreBlock extends LegacyOreBlock {
    public enum Type { TRINITITE, CHARRED_PLANKS }

    private final Type type;

    public WasteOreBlock(Properties properties, Type type) {
        super(properties, type == Type.TRINITITE
                ? Drop.item("trinitite", 1, 1, false)
                : Drop.vanilla("charcoal", 1, 1, false));
        this.type = type;
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return type == Type.CHARRED_PLANKS;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return type == Type.CHARRED_PLANKS ? 20 : 0;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return type == Type.CHARRED_PLANKS ? 5 : 0;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && type == Type.TRINITITE && entity instanceof LivingEntity living) {
            LegacyRadiationBlockEffects.refresh(living, 0);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (type == Type.TRINITITE) {
            level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + random.nextFloat(), pos.getY() + 1.1D,
                    pos.getZ() + random.nextFloat(), 0.0D, 0.0D, 0.0D);
        }
    }
}
