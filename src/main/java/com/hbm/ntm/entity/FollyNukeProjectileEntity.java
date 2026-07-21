package com.hbm.ntm.entity;

import com.hbm.ntm.nuclear.MushroomCloudEntity;
import com.hbm.ntm.nuclear.NuclearExplosionEntity;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class FollyNukeProjectileEntity extends Projectile {
    public static final int LIFETIME = 600;
    public static final double SPEED = 4.0D;
    public static final double GRAVITY = 0.015D;

    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(
            FollyNukeProjectileEntity.class, EntityDataSerializers.FLOAT);

    public FollyNukeProjectileEntity(EntityType<? extends FollyNukeProjectileEntity> type, Level level) {
        super(type, level);
    }

    public FollyNukeProjectileEntity(ServerLevel level, LivingEntity owner, float damage,
                                     Vec3 origin, Vec3 heading) {
        this(ModEntities.FOLLY_NUKE_PROJECTILE.get(), level);
        setOwner(owner);
        entityData.set(DAMAGE, damage);
        setPos(origin);
        setDeltaMovement(heading.normalize());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 0.0F);
    }

    public float damage() { return entityData.get(DAMAGE); }

    public Vec3 direction() {
        Vec3 movement = getDeltaMovement();
        return movement.lengthSqr() > 1.0E-8D ? movement.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > LIFETIME) {
            discard();
            return;
        }
        Vec3 movement = getDeltaMovement();
        Vec3 step = movement.normalize().scale(SPEED);
        Vec3 start = position();
        Vec3 end = start.add(step);
        if (level() instanceof ServerLevel level) {
            level.getChunkAt(blockPosition());
            BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 collisionEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
            AABB sweep = getBoundingBox().expandTowards(step).inflate(1.0D);
            Entity nearest = null;
            Vec3 nearestHit = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Entity candidate : level.getEntities(this, sweep, this::isImpactCandidate)) {
                Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(start, collisionEnd);
                if (hit.isEmpty()) continue;
                double distance = start.distanceToSqr(hit.get());
                if (distance < nearestDistance) {
                    nearest = candidate;
                    nearestHit = hit.get();
                    nearestDistance = distance;
                }
            }
            if (nearest != null && tickCount >= 2) impact(level, nearestHit);
            else if (blockHit.getType() != HitResult.Type.MISS) impact(level, blockHit.getLocation());
        }
        if (isAlive()) {
            setPos(end);
            setDeltaMovement(direction().add(0.0D, -GRAVITY, 0.0D));
        }
    }

    private boolean isImpactCandidate(Entity entity) {
        return entity != getOwner() && entity.isAlive() && !entity.isSpectator() && entity.isPickable();
    }

    private void impact(ServerLevel level, Vec3 hit) {
        level.addFreshEntity(NuclearExplosionEntity.create(level, 100, hit.x, hit.y, hit.z));
        MushroomCloudEntity cloud = new MushroomCloudEntity(ModEntities.MUSHROOM_CLOUD.get(), level);
        cloud.setPos(hit);
        cloud.configure(100);
        level.addFreshEntity(cloud);
        setPos(hit);
        discard();
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
}
