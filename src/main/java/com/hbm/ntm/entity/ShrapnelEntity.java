package com.hbm.ntm.entity;

import com.hbm.ntm.radiation.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Fast metal fragment. Hurts immediately, admits death after five ticks. */
public final class ShrapnelEntity extends Projectile {
    private static final EntityDataAccessor<Byte> VARIANT =
            SynchedEntityData.defineId(ShrapnelEntity.class, EntityDataSerializers.BYTE);

    public ShrapnelEntity(EntityType<? extends ShrapnelEntity> type, net.minecraft.world.level.Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VARIANT, (byte) 0);
    }

    /** Whether this fragment paid for the flame trail upgrade. */
    public void setTrail(boolean trail) {
        entityData.set(VARIANT, (byte) (trail ? 1 : 0));
    }

    public boolean trail() {
        return entityData.get(VARIANT) == 1;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 movement = getDeltaMovement();

        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onImpact(hit);
            }
        }
        if (isRemoved()) {
            return;
        }

        setPos(position().add(movement));

        if (level().isClientSide && trail()) {
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        }

        // Ordinary throwable drag and gravity, despite extraordinary circumstances.
        float drag = isInWater() ? 0.8F : 0.99F;
        setDeltaMovement(movement.scale(drag).subtract(0.0D, 0.03D, 0.0D));
        updateRotation();
        checkInsideBlocks();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity.isAlive() && entity.isPickable() && !entity.isSpectator();
    }

    private void onImpact(HitResult hit) {
        if (level().isClientSide) {
            return;
        }
        if (hit instanceof EntityHitResult entityHit) {
            // Flat fifteen damage. Age discrimination prohibited.
            entityHit.getEntity().hurt(level().damageSources().source(ModDamageTypes.SHRAPNEL, this), 15.0F);
        }
        // Young shrapnel refuses to acknowledge collisions.
        if (tickCount > 5) {
            discard();
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.LAVA, getX(), getY(), getZ(), 5,
                        0.0D, 0.0D, 0.0D, 0.0D);
                serverLevel.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("variant", entityData.get(VARIANT));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(VARIANT, tag.getByte("variant"));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
