package com.hbm.ntm.entity;

import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.weapon.Shotgun12GaugeAmmoType;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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

/** Two hundred fifty blocks of cyan notice that submunitions are coming. */
public final class ShredderBeamEntity extends Projectile {
    private static final double RANGE = 250.0D;
    private static final int EXPIRES = 5;
    private static final double BLOCK_AOE = 0.75D;

    private static final EntityDataAccessor<Integer> AMMO =
            SynchedEntityData.defineId(ShredderBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(ShredderBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_LENGTH =
            SynchedEntityData.defineId(ShredderBeamEntity.class, EntityDataSerializers.FLOAT);

    public ShredderBeamEntity(EntityType<? extends ShredderBeamEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    /**
     * @param inaccuracy the receiver's {@code calcSpread} in degrees (0 aimed / 0.025 hipfire plus wear); the
     *                   both yaw and pitch receive {@code gaussian * inaccuracy}.
     * @param offset local muzzle offset (x=side, y=up, z=forward), the receiver projectile offset.
     */
    public ShredderBeamEntity(ServerLevel level, LivingEntity shooter, Shotgun12GaugeAmmoType ammo,
                              float beamDamage, float inaccuracy, Vec3 offset) {
        this(ModEntities.SHREDDER_BEAM.get(), level);
        setOwner(shooter);
        entityData.set(AMMO, ammo.legacyMetadata());
        entityData.set(DAMAGE, beamDamage);

        float yaw = shooter.getYRot() + (float) random.nextGaussian() * inaccuracy;
        float pitch = shooter.getXRot() + (float) random.nextGaussian() * inaccuracy;
        setYRot(yaw);
        setXRot(pitch);
        yRotO = yaw;
        xRotO = pitch;

        Vec3 rotated = offset.xRot(-pitch * Mth.DEG_TO_RAD).yRot(-yaw * Mth.DEG_TO_RAD);
        setPos(shooter.getEyePosition().add(rotated));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AMMO, Shotgun12GaugeAmmoType.BUCKSHOT.legacyMetadata());
        builder.define(DAMAGE, 0.0F);
        builder.define(BEAM_LENGTH, (float) RANGE);
    }

    public Shotgun12GaugeAmmoType ammoType() {
        return Shotgun12GaugeAmmoType.fromLegacyMetadata(entityData.get(AMMO));
    }

    public float beamDamage() {
        return entityData.get(DAMAGE);
    }

    public float beamLength() {
        return entityData.get(BEAM_LENGTH);
    }

    /** Instantaneous hitscan + impact, mirroring EntityBulletBeamBase.performHitscan on spawn. */
    public void performHitscan() {
        if (!(level() instanceof ServerLevel server)) return;

        double yaw = getYRot() * Mth.DEG_TO_RAD;
        double pitch = getXRot() * Mth.DEG_TO_RAD;
        Vec3 heading = new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).scale(RANGE);

        Vec3 start = position();
        Vec3 end = start.add(heading);
        BlockHitResult blockHit = server.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 entityEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        Entity nearestEntity = null;
        Vec3 nearestHit = null;
        double nearestDist = 0.0D;
        AABB sweep = getBoundingBox().expandTowards(heading).inflate(1.0D);
        for (Entity candidate : server.getEntities(this, sweep, this::canHit)) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(start, entityEnd);
            if (hit.isEmpty()) continue;
            double dist = start.distanceToSqr(hit.get());
            if (nearestEntity == null || dist < nearestDist) {
                nearestEntity = candidate;
                nearestHit = hit.get();
                nearestDist = dist;
            }
        }

        if (nearestEntity != null) {
            impactEntity(server, nearestEntity, nearestHit);
            entityData.set(BEAM_LENGTH, (float) start.distanceTo(nearestHit));
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            impactBlock(server, blockHit);
            entityData.set(BEAM_LENGTH, (float) start.distanceTo(blockHit.getLocation()));
        } else {
            entityData.set(BEAM_LENGTH, (float) RANGE);
        }
    }

    private boolean canHit(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !entity.isPickable()) return false;
        return entity != getOwner();
    }

    private void impactBlock(ServerLevel server, BlockHitResult blockHit) {
        Direction dir = blockHit.getDirection();
        Vec3 face = Vec3.atLowerCornerOf(dir.getNormal());
        Vec3 hitVec = blockHit.getLocation().add(face.scale(0.1D));
        // TODO: Send the old plasmablast pulse when that particle is implemented.

        Entity owner = getOwner();
        DamageSource source = server.damageSources().source(ModDamageTypes.LASER, this, owner);
        AABB box = new AABB(hitVec, hitVec).inflate(BLOCK_AOE);
        for (Entity e : server.getEntities(this, box, Entity::isAlive)) {
            if (e instanceof LivingEntity living) {
                living.invulnerableTime = 0;
                Vec3 keep = living.getDeltaMovement();
                living.hurt(source, beamDamage());
                living.setDeltaMovement(keep);
            } else {
                e.hurt(source, beamDamage());
            }
        }

        spawnSubmunitions(server, hitVec, () -> face);
    }

    private void impactEntity(ServerLevel server, Entity target, Vec3 hitVec) {
        // The beam itself does no entity damage; the submunition burst gets that privilege.
        spawnSubmunitions(server, hitVec, () -> new Vec3(
                random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize());
    }

    private void spawnSubmunitions(ServerLevel server, Vec3 hitVec, java.util.function.Supplier<Vec3> direction) {
        Shotgun12GaugeAmmoType ammo = ammoType();
        float subDamage = beamDamage() * ammo.damageMultiplier();
        LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
        int projectiles = ammo.projectiles();
        for (int i = 0; i < projectiles; i++) {
            ShredderSubmunitionEntity sub = new ShredderSubmunitionEntity(
                    server, owner, ammo, subDamage, hitVec, direction.get());
            server.addFreshEntity(sub);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > EXPIRES) discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("ammo", entityData.get(AMMO));
        tag.putFloat("damage", entityData.get(DAMAGE));
        tag.putFloat("beamLength", entityData.get(BEAM_LENGTH));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // EntityBulletBeamBase.readEntityFromNBT immediately set the beam dead; a persisted beam is inert.
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
