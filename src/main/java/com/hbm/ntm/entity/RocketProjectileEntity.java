package com.hbm.ntm.entity;

import com.hbm.ntm.explosion.MineExplosion;
import com.hbm.ntm.network.MineBlastPayload;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.weapon.RocketAmmoType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/** Source MK4 rocket: zero launch velocity, then 0.4/tick acceleration up to seven. */
public final class RocketProjectileEntity extends Projectile {
    private static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(
            RocketProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(
            RocketProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(
            RocketProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_X = SynchedEntityData.defineId(
            RocketProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_Y = SynchedEntityData.defineId(
            RocketProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_Z = SynchedEntityData.defineId(
            RocketProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LOCK_TARGET = SynchedEntityData.defineId(
            RocketProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLIGHT_MODE = SynchedEntityData.defineId(
            RocketProjectileEntity.class, EntityDataSerializers.INT);

    private float previousSpeed;

    public RocketProjectileEntity(EntityType<? extends RocketProjectileEntity> type, Level level) {
        super(type, level);
    }

    public RocketProjectileEntity(ServerLevel level, LivingEntity owner, RocketAmmoType ammo,
                                  float damage, float spread, Vec3 origin, Vec3 heading) {
        this(level, owner, ammo, damage, spread, origin, heading, null, FlightMode.STANDARD);
    }

    public RocketProjectileEntity(ServerLevel level, LivingEntity owner, RocketAmmoType ammo,
                                  float damage, float spread, Vec3 origin, Vec3 heading,
                                  Entity lockTarget) {
        this(level, owner, ammo, damage, spread, origin, heading, lockTarget, FlightMode.STANDARD);
    }

    public RocketProjectileEntity(ServerLevel level, LivingEntity owner, RocketAmmoType ammo,
                                  float damage, float spread, Vec3 origin, Vec3 heading,
                                  FlightMode flightMode) {
        this(level, owner, ammo, damage, spread, origin, heading, null, flightMode);
    }

    public RocketProjectileEntity(ServerLevel level, LivingEntity owner, RocketAmmoType ammo,
                                  float damage, float spread, Vec3 origin, Vec3 heading,
                                  Entity lockTarget, FlightMode flightMode) {
        this(ModEntities.ROCKET_PROJECTILE.get(), level);
        setOwner(owner);
        entityData.set(AMMO, ammo.legacyMetadata());
        entityData.set(DAMAGE, damage);
        setPos(origin);
        double inaccuracy = 0.0075D * spread;
        Vec3 direction = new Vec3(
                heading.x + random.nextGaussian() * inaccuracy,
                heading.y + random.nextGaussian() * inaccuracy,
                heading.z + random.nextGaussian() * inaccuracy).normalize();
        setDirection(direction);
        entityData.set(SPEED, 0.0F);
        previousSpeed = 0.0F;
        setDeltaMovement(Vec3.ZERO);
        entityData.set(LOCK_TARGET, lockTarget == null ? -1 : lockTarget.getId());
        entityData.set(FLIGHT_MODE, flightMode.ordinal());
        updateAuthoredRotation(direction);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AMMO, RocketAmmoType.HIGH_EXPLOSIVE.legacyMetadata());
        builder.define(DAMAGE, 0.0F);
        builder.define(SPEED, 0.0F);
        builder.define(DIRECTION_X, 0.0F);
        builder.define(DIRECTION_Y, 0.0F);
        builder.define(DIRECTION_Z, 1.0F);
        builder.define(LOCK_TARGET, -1);
        builder.define(FLIGHT_MODE, FlightMode.STANDARD.ordinal());
    }

    public RocketAmmoType ammoType() {
        return RocketAmmoType.fromLegacyMetadata(entityData.get(AMMO));
    }

    public float damage() { return entityData.get(DAMAGE); }
    public float speed() { return entityData.get(SPEED); }
    public float previousSpeed() { return previousSpeed; }
    public int lockTargetId() { return entityData.get(LOCK_TARGET); }
    public FlightMode flightMode() { return FlightMode.fromOrdinal(entityData.get(FLIGHT_MODE)); }
    public int lifeTicks() {
        return flightMode() == FlightMode.PLAYER_GUIDED ? 400 : ammoType().lifeTicks();
    }

    public float interpolatedSpeed(float partialTick) {
        return Mth.lerp(partialTick, previousSpeed, speed());
    }

    public Vec3 direction() {
        Vec3 direction = new Vec3(entityData.get(DIRECTION_X), entityData.get(DIRECTION_Y),
                entityData.get(DIRECTION_Z));
        return direction.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
    }

    @Override
    public void tick() {
        super.tick();
        RocketAmmoType ammo = ammoType();
        if (tickCount > lifeTicks()) {
            discard();
            return;
        }

        Vec3 direction = direction();
        float currentSpeed = speed();
        previousSpeed = currentSpeed;
        Vec3 step = direction.scale(currentSpeed);
        Vec3 start = position();
        Vec3 end = start.add(step);

        if (!level().isClientSide && currentSpeed > 0.0F) {
            BlockHitResult blockHit = level().clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 collisionEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
            AABB sweep = getBoundingBox().expandTowards(step).inflate(1.0D);
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
        }

        if (isAlive()) {
            setPos(end);
            direction = guidedDirection(direction);
            setDirection(direction);
            float nextSpeed = accelerate(ammo, currentSpeed);
            entityData.set(SPEED, nextSpeed);
            setDeltaMovement(direction.scale(nextSpeed));
            updateAuthoredRotation(direction);
        }
    }

    /** EntityBulletBaseMK4 homes after movement and before its per-tick rocket acceleration. */
    private Vec3 guidedDirection(Vec3 current) {
        if (flightMode() == FlightMode.PLAYER_GUIDED) return playerGuidedDirection(current);
        if (level().isClientSide || lockTargetId() < 0) return current;
        Entity target = level().getEntity(lockTargetId());
        if (target == null || !target.isAlive()) return current;
        Vec3 delta = new Vec3(target.getX() - getX(),
                target.getY() + target.getBbHeight() * 0.5D - getY(),
                target.getZ() - getZ());
        float turn = Math.min(0.005F * tickCount, 1.0F);
        Vec3 steered = new Vec3(
                Mth.lerp(turn, current.x, delta.x),
                Mth.lerp(turn, current.y, delta.y),
                Mth.lerp(turn, current.z, delta.z));
        return steered.lengthSqr() < 1.0E-8D ? current : steered.normalize();
    }

    /** XFactoryRocket steeringAccelerate snaps Quadro rockets to the owner's 200-block view ray. */
    private Vec3 playerGuidedDirection(Vec3 current) {
        if (level().isClientSide || !(getOwner() instanceof Player player)) return current;
        if (distanceTo(player) > 100.0F) return current;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SednaGunItem gun) || !gun.gunAiming(held)) return current;

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(200.0D));
        BlockHitResult hit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 delta = hit.getLocation().subtract(position());
        return delta.length() < 3.0D ? current : delta.normalize();
    }

    private float accelerate(RocketAmmoType ammo, float currentSpeed) {
        if (flightMode() != FlightMode.PLAYER_GUIDED || !(getOwner() instanceof Player)) {
            return ammo.accelerate(currentSpeed);
        }
        // Acceleration is a double. Repeated 0.4 additions cross the
        // `accel < 4` threshold at 4.4; the epsilon retains that sequence in this float sync field.
        return currentSpeed < 4.0001F ? currentSpeed + ammo.accelerationPerTick() : currentSpeed;
    }

    private boolean isImpactCandidate(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !entity.isPickable() || tickCount < 3) return false;
        return entity != getOwner() || tickCount >= ammoType().selfDamageDelay();
    }

    private void impact(Vec3 hit, Entity target) {
        if (!(level() instanceof ServerLevel level)) return;
        RocketAmmoType ammo = ammoType();
        blast(level, hit, ammo.impactExplosionRadius(), damage());

        if (ammo.family() == RocketAmmoType.Family.SHAPED_CHARGE && target != null) {
            directShapedCharge(level, target);
        }
        if (ammo.destroysBlocks()) {
            MineExplosion.blastBlocks(level, hit.x, hit.y, hit.z, 5.0F, 32, false, getOwner());
        }
        if (ammo.createsLingeringFire()) {
            LingeringFireEntity.Kind kind = ammo.family() == RocketAmmoType.Family.WHITE_PHOSPHORUS
                    ? LingeringFireEntity.Kind.PHOSPHORUS : LingeringFireEntity.Kind.FIRE;
            level.addFreshEntity(new LingeringFireEntity(ModEntities.LINGERING_FIRE.get(), level,
                    hit.x, hit.y, hit.z, ammo.lingeringTicks(), 6.0D, 2.0D, kind));
            placeSourceFire(level, hit);
        }
        setPos(hit);
        discard();
    }

    private void directShapedCharge(ServerLevel level, Entity target) {
        var source = level.damageSources().explosion(this, getOwner());
        if (target instanceof LivingEntity living) {
            Vec3 previousMotion = living.getDeltaMovement();
            living.invulnerableTime = 0;
            float applied = MineExplosion.compensateForArmorPiercing(
                    living, source, damage() * 3.0F, 5.0F, 0.2F);
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

    private void blast(ServerLevel level, Vec3 center, float radius, float fixedDamage) {
        MineExplosion.blastEntities(level, center.x, center.y, center.z,
                radius, fixedDamage, 1.0D, 0.0F, 0.0F, 1.0F,
                getOwner(), getOwner(), true);
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, 200.0D,
                new MineBlastPayload(center.x, center.y, center.z, 10, 1.0F));
    }

    private static void placeSourceFire(ServerLevel level, Vec3 hit) {
        BlockPos origin = BlockPos.containing(hit);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -2, -2), origin.offset(2, 2, 2))) {
            if (!level.getBlockState(pos).isAir()) continue;
            for (Direction direction : Direction.values()) {
                BlockPos support = pos.relative(direction);
                BlockState state = level.getBlockState(support);
                if (!state.isFlammable(level, support, direction.getOpposite())) continue;
                level.setBlock(pos, BaseFireBlock.getState(level, pos), 11);
                break;
            }
        }
    }

    private void setDirection(Vec3 direction) {
        entityData.set(DIRECTION_X, (float) direction.x);
        entityData.set(DIRECTION_Y, (float) direction.y);
        entityData.set(DIRECTION_Z, (float) direction.z);
    }

    private void updateAuthoredRotation(Vec3 direction) {
        float horizontal = Mth.sqrt((float) (direction.x * direction.x + direction.z * direction.z));
        setYRot((float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(AMMO, tag.getInt("Ammo"));
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(SPEED, tag.getFloat("Speed"));
        previousSpeed = entityData.get(SPEED);
        setDirection(new Vec3(tag.getDouble("DirectionX"), tag.getDouble("DirectionY"),
                tag.getDouble("DirectionZ")));
        entityData.set(LOCK_TARGET, tag.getInt("LockTarget"));
        entityData.set(FLIGHT_MODE, tag.getInt("FlightMode"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Ammo", entityData.get(AMMO));
        tag.putFloat("Damage", entityData.get(DAMAGE));
        tag.putFloat("Speed", entityData.get(SPEED));
        Vec3 direction = direction();
        tag.putDouble("DirectionX", direction.x);
        tag.putDouble("DirectionY", direction.y);
        tag.putDouble("DirectionZ", direction.z);
        tag.putInt("LockTarget", entityData.get(LOCK_TARGET));
        tag.putInt("FlightMode", entityData.get(FLIGHT_MODE));
    }

    public enum FlightMode {
        STANDARD,
        PLAYER_GUIDED,
        MISSILE_LAUNCHER;

        private static FlightMode fromOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : STANDARD;
        }
    }
}
