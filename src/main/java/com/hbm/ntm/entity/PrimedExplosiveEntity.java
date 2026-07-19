package com.hbm.ntm.entity;

import com.hbm.ntm.block.PrimedExplosiveBlock;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class PrimedExplosiveEntity extends PrimedTnt {
    @Nullable
    private LivingEntity hbmOwner;

    public PrimedExplosiveEntity(EntityType<? extends PrimedExplosiveEntity> type, Level level) {
        super(type, level);
    }

    public PrimedExplosiveEntity(Level level, double x, double y, double z, @Nullable LivingEntity owner,
                                 BlockState explosiveState, int fuse) {
        this(ModEntities.PRIMED_EXPLOSIVE.get(), level);
        setPos(x, y, z);
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        setDeltaMovement(-Math.sin(angle) * 0.02D, 0.2D, -Math.cos(angle) * 0.02D);
        xo = x;
        yo = y;
        zo = z;
        hbmOwner = owner;
        setBlockState(explosiveState);
        setFuse(fuse);
    }

    @Override
    public void tick() {
        handlePortal();
        applyGravity();
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.98D));
        if (onGround()) {
            setDeltaMovement(getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
        }

        int fuse = getFuse();
        setFuse(fuse - 1);
        if (fuse <= 0) {
            discard();
            if (!level().isClientSide) {
                explode();
            }
        } else {
            updateInWaterStateAndDoFluidPushing();
            if (level().isClientSide) {
                level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5D, getZ(), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    protected void explode() {
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                && getBlockState().getBlock() instanceof PrimedExplosiveBlock explosive) {
            explosive.detonatePrimed(serverLevel, getX(), getY(), getZ(), this);
        }
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        return hbmOwner;
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof PrimedExplosiveEntity explosive) {
            hbmOwner = explosive.hbmOwner;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }
}
