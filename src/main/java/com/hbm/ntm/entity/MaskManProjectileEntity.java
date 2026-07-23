package com.hbm.ntm.entity;

import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class MaskManProjectileEntity extends Projectile {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(
            MaskManProjectileEntity.class, EntityDataSerializers.INT);
    private final Set<Integer> struckEntities = new HashSet<>();

    public MaskManProjectileEntity(EntityType<? extends MaskManProjectileEntity> type, Level level) {
        super(type, level);
    }

    public static MaskManProjectileEntity aimed(ServerLevel level, LivingEntity owner,
                                                 LivingEntity target, Kind kind,
                                                 float speed, float deviation) {
        MaskManProjectileEntity projectile = new MaskManProjectileEntity(
                ModEntities.MASK_MAN_PROJECTILE.get(), level);
        projectile.setOwner(owner);
        projectile.entityData.set(KIND, kind.ordinal());
        Vec3 origin = owner.position().add(0.0D, owner.getEyeHeight() - 0.1D, 0.0D);
        Vec3 delta = target.position().add(0.0D, target.getBbHeight() / 3.0D, 0.0D)
                .subtract(origin);
        projectile.setPos(origin.add(delta.normalize()));
        double scatter = deviation * 0.0075D;
        projectile.setDeltaMovement(new Vec3(
                delta.x + projectile.random.nextGaussian() * scatter,
                delta.y + projectile.random.nextGaussian() * scatter,
                delta.z + projectile.random.nextGaussian() * scatter).normalize().scale(speed));
        return projectile;
    }

    public static MaskManProjectileEntity bolt(ServerLevel level, Entity owner,
                                                Vec3 origin, Vec3 direction) {
        MaskManProjectileEntity bolt = new MaskManProjectileEntity(
                ModEntities.MASK_MAN_PROJECTILE.get(), level);
        bolt.setOwner(owner);
        bolt.entityData.set(KIND, Kind.BOLT.ordinal());
        bolt.setPos(origin);
        bolt.setDeltaMovement(direction.normalize().add(
                bolt.random.nextGaussian() * 0.000375D,
                bolt.random.nextGaussian() * 0.000375D,
                bolt.random.nextGaussian() * 0.000375D).normalize().scale(0.5D));
        return bolt;
    }

    public static MaskManProjectileEntity meteor(ServerLevel level, Entity owner, Vec3 impact) {
        MaskManProjectileEntity meteor = new MaskManProjectileEntity(
                ModEntities.MASK_MAN_PROJECTILE.get(), level);
        meteor.setOwner(owner);
        meteor.entityData.set(KIND, Kind.METEOR.ordinal());
        meteor.setPos(impact.add(0.0D, 30.0D + meteor.random.nextInt(10), 0.0D));
        meteor.setDeltaMovement(0.0D, -1.0D, 0.0D);
        return meteor;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, Kind.ORB.ordinal());
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(entityData.get(KIND), Kind.values().length)];
    }

    @Override
    public void tick() {
        super.tick();
        Kind kind = kind();
        if (tickCount > kind.maxAge) {
            discard();
            return;
        }

        if (level().isClientSide) {
            clientParticles(kind);
        } else {
            if (kind == Kind.ORB && tickCount % 10 == 5 && level() instanceof ServerLevel server) {
                for (Player player : level().players()) {
                    if (!player.isAlive() || distanceToSqr(player) > 2_500.0D) continue;
                    Vec3 direction = player.getEyePosition().subtract(position());
                    server.addFreshEntity(bolt(server, getOwner(), position(), direction));
                }
            }
            if (sweepCollision(kind)) return;
        }

        Vec3 movement = getDeltaMovement();
        setPos(position().add(movement));
        if (kind.gravity != 0.0D) setDeltaMovement(movement.add(0.0D, -kind.gravity, 0.0D));
    }

    private boolean sweepCollision(Kind kind) {
        Vec3 start = position();
        Vec3 movement = getDeltaMovement();
        Vec3 end = start.add(movement);
        BlockHitResult blockHit = level().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 collisionEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        Entity nearestEntity = null;
        Vec3 nearestHit = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity candidate : level().getEntities(this,
                getBoundingBox().expandTowards(movement).inflate(0.5D), this::canHit)) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.25D).clip(start, collisionEnd);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < nearestDistance) {
                nearestDistance = start.distanceToSqr(hit.get());
                nearestEntity = candidate;
                nearestHit = hit.get();
            }
        }

        if (nearestEntity != null) {
            return impact(kind, nearestHit, nearestEntity);
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            return impact(kind, blockHit.getLocation(), null);
        }
        return false;
    }

    private boolean canHit(Entity entity) {
        return entity.isAlive() && entity.isPickable() && entity != getOwner()
                && !(entity instanceof MaskManProjectileEntity)
                && !struckEntities.contains(entity.getId()) && tickCount > 1;
    }

    private boolean impact(Kind kind, Vec3 hit, Entity entity) {
        if (!(level() instanceof ServerLevel server)) return false;
        if (entity != null) {
            float damage = kind.minDamage == kind.maxDamage ? kind.minDamage
                    : kind.minDamage + random.nextFloat() * (kind.maxDamage - kind.minDamage);
            entity.hurt(server.damageSources().source(ModDamageTypes.LASER, this, getOwner()), damage);
            if (kind == Kind.METEOR) entity.setRemainingFireTicks(60);
            if (kind == Kind.BOLT || kind == Kind.TRACER) {
                struckEntities.add(entity.getId());
                return false;
            }
        }
        setPos(hit);
        if (kind.explosion > 0.0F) {
            server.explode(this, hit.x, hit.y, hit.z, kind.explosion,
                    false, Level.ExplosionInteraction.NONE);
        }
        if (kind == Kind.TRACER) server.addFreshEntity(meteor(server, getOwner(), hit));
        if (kind == Kind.METEOR) scatterFire(server, BlockPos.containing(hit));
        discard();
        return true;
    }

    private void scatterFire(ServerLevel level, BlockPos center) {
        BlockPos[] positions = {
                center,
                center.above(),
                center.below(),
                center.north(),
                center.south(),
                center.east(),
                center.west()
        };
        for (BlockPos pos : positions) {
            if (random.nextInt(3) != 0) continue;
            if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, null)) {
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
            }
        }
    }

    private void clientParticles(Kind kind) {
        switch (kind) {
            case ORB -> {
                level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
                level().addParticle(ParticleTypes.DRAGON_BREATH, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            }
            case BOLT -> level().addParticle(ParticleTypes.DRAGON_BREATH,
                    getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            case TRACER -> level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            case ROCKET -> level().addParticle(ParticleTypes.SMOKE,
                    getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            case METEOR -> {
                for (int index = 0; index < 5; index++) {
                    level().addParticle(ParticleTypes.FLAME,
                            getX() + random.nextDouble() * 0.5D - 0.25D,
                            getY() + random.nextDouble() * 0.5D - 0.25D,
                            getZ() + random.nextDouble() * 0.5D - 0.25D,
                            0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIND, tag.getInt("Kind"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kind", entityData.get(KIND));
    }

    public enum Kind {
        ORB(100.0F, 100.0F, 1.5F, 0.0D, 60),
        BOLT(15.0F, 20.0F, 0.5F, 0.0D, 100),
        ROCKET(15.0F, 20.0F, 5.0F, 0.1D, 300),
        TRACER(15.0F, 20.0F, 0.0F, 0.0D, 100),
        METEOR(20.0F, 30.0F, 2.5F, 0.1D, 300);

        private final float minDamage;
        private final float maxDamage;
        private final float explosion;
        private final double gravity;
        private final int maxAge;

        Kind(float minDamage, float maxDamage, float explosion, double gravity, int maxAge) {
            this.minDamage = minDamage;
            this.maxDamage = maxDamage;
            this.explosion = explosion;
            this.gravity = gravity;
            this.maxAge = maxAge;
        }
    }
}
