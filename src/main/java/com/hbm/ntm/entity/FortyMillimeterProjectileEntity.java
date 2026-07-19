package com.hbm.ntm.entity;

import com.hbm.ntm.explosion.MineExplosion;
import com.hbm.ntm.network.ChargeBlastPayload;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.weapon.FortyMillimeterAmmoType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.Optional;

/** Slow, gravity-driven XFactory40mm projectile. */
public final class FortyMillimeterProjectileEntity extends Projectile {
    private static final double MOTION_MULTIPLIER = 2.0D;
    private static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(
            FortyMillimeterProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(
            FortyMillimeterProjectileEntity.class, EntityDataSerializers.FLOAT);

    public FortyMillimeterProjectileEntity(EntityType<? extends FortyMillimeterProjectileEntity> type, Level level) {
        super(type, level);
    }

    public FortyMillimeterProjectileEntity(ServerLevel level, LivingEntity owner,
                                            FortyMillimeterAmmoType ammo, float damage,
                                            float spread, Vec3 origin, Vec3 heading) {
        this(ModEntities.FORTY_MILLIMETER_PROJECTILE.get(), level);
        setOwner(owner);
        entityData.set(AMMO, ammo.legacyMetadata());
        entityData.set(DAMAGE, damage);
        setPos(origin);
        double inaccuracy = 0.0075D * spread;
        Vec3 velocity = new Vec3(
                heading.x + random.nextGaussian() * inaccuracy,
                heading.y + random.nextGaussian() * inaccuracy,
                heading.z + random.nextGaussian() * inaccuracy).normalize();
        setDeltaMovement(velocity);
        updateRotation();
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AMMO, FortyMillimeterAmmoType.SIGNAL_FLARE.legacyMetadata());
        builder.define(DAMAGE, 0.0F);
    }

    public FortyMillimeterAmmoType ammoType() {
        return FortyMillimeterAmmoType.fromLegacyMetadata(entityData.get(AMMO));
    }

    public float damage() { return entityData.get(DAMAGE); }

    @Override
    public void tick() {
        super.tick();
        FortyMillimeterAmmoType ammo = ammoType();
        if (tickCount > ammo.lifeTicks()) {
            discard();
            return;
        }

        Vec3 velocity = getDeltaMovement();
        Vec3 step = velocity.scale(MOTION_MULTIPLIER);
        Vec3 start = position();
        Vec3 end = start.add(step);
        if (level().isClientSide) {
            spawnTrail(ammo);
            setPos(end);
            setDeltaMovement(velocity.add(0.0D, -ammo.gravity(), 0.0D));
            updateRotation();
            return;
        }

        BlockHitResult blockHit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 collisionEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        AABB sweep = getBoundingBox().expandTowards(step).inflate(0.5D);
        Entity nearest = null;
        Vec3 nearestHit = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity candidate : level().getEntities(this, sweep, this::isImpactCandidate)) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(start, collisionEnd);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestHit = hit.get();
                nearestDistance = distance;
            }
        }

        if (nearest != null) impact(nearestHit, nearest);
        else if (blockHit.getType() != HitResult.Type.MISS) impact(blockHit.getLocation(), null);

        if (isAlive()) {
            setPos(end);
            setDeltaMovement(velocity.add(0.0D, -ammo.gravity(), 0.0D));
            updateRotation();
        }
    }

    private boolean isImpactCandidate(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !entity.isPickable()) return false;
        return entity != getOwner() || tickCount >= 3;
    }

    private void impact(Vec3 hit, Entity target) {
        if (!(level() instanceof ServerLevel level)) return;
        FortyMillimeterAmmoType ammo = ammoType();
        if (ammo.isFlare()) {
            if (target instanceof LivingEntity living) living.setRemainingFireTicks(
                    Math.max(living.getRemainingFireTicks(), 200));
            if (target != null) {
                target.hurt(level.damageSources().source(ModDamageTypes.BULLET, this, getOwner()), damage());
            }
            setPos(hit);
            discard();
            return;
        }

        blast(level, hit, ammo.impactExplosionRadius(), damage());
        if (ammo.family() == FortyMillimeterAmmoType.Family.HEAT && target != null) {
            var source = level.damageSources().explosion(this, getOwner());
            if (target instanceof LivingEntity living) {
                Vec3 previousMotion = living.getDeltaMovement();
                living.invulnerableTime = 0;
                float applied = MineExplosion.compensateForArmorPiercing(
                        living, source, damage() * 3.0F, 3.0F, 0.15F);
                boolean hurt = living.hurt(source, applied);
                living.setDeltaMovement(previousMotion);
                if (hurt && getOwner() != null) {
                    double dx = living.getX() - getOwner().getX();
                    double dz = living.getZ() - getOwner().getZ();
                    if (dx * dx + dz * dz < 1.0E-4D) {
                        dx = (random.nextDouble() - random.nextDouble()) * 0.01D;
                        dz = (random.nextDouble() - random.nextDouble()) * 0.01D;
                    }
                    living.knockback(0.5D, -dx, -dz);
                }
            } else {
                target.hurt(source, damage() * 3.0F);
            }
        }
        if (ammo.family() == FortyMillimeterAmmoType.Family.DEMOLITION) {
            MineExplosion.blastBlocks(level, hit.x, hit.y, hit.z, 5.0F, 32, false, getOwner());
        }
        if (ammo.family() == FortyMillimeterAmmoType.Family.INCENDIARY
                || ammo.family() == FortyMillimeterAmmoType.Family.PHOSPHORUS) {
            boolean phosphorus = ammo.family() == FortyMillimeterAmmoType.Family.PHOSPHORUS;
            level.addFreshEntity(new LingeringFireEntity(ModEntities.LINGERING_FIRE.get(), level,
                    hit.x, hit.y, hit.z, phosphorus ? 400 : 200, phosphorus));
            placeAdjacentFire(level, hit);
        }
        setPos(hit);
        discard();
    }

    private void blast(ServerLevel level, Vec3 center, float radius, float fixedDamage) {
        MineExplosion.blastEntities(level, center.x, center.y, center.z,
                radius, fixedDamage, 1.0D, 0.0F, 0.0F, 1.0F, this, getOwner());
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, 200.0D,
                new ChargeBlastPayload(center.x, center.y, center.z, false));
    }

    private static void placeAdjacentFire(ServerLevel level, Vec3 hit) {
        BlockPos origin = BlockPos.containing(hit);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
            if (!level.getBlockState(pos).isAir()) continue;
            for (Direction direction : Direction.values()) {
                BlockPos support = pos.relative(direction);
                BlockState state = level.getBlockState(support);
                if (!state.ignitedByLava()) continue;
                level.setBlock(pos, BaseFireBlock.getState(level, pos), 11);
                break;
            }
        }
    }

    private void spawnTrail(FortyMillimeterAmmoType ammo) {
        if (ammo.isFlare()) {
            level().addParticle(new DustParticleOptions(new Vector3f(1.0F, 0.08F, 0.03F), 1.5F),
                    getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        } else if (tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(AMMO, tag.getInt("Ammo"));
        entityData.set(DAMAGE, tag.getFloat("Damage"));
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Ammo", entityData.get(AMMO));
        tag.putFloat("Damage", entityData.get(DAMAGE));
    }
}
