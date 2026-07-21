package com.hbm.ntm.entity;

import com.hbm.ntm.explosion.MineExplosion;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.weapon.AberratorAmmoType;
import com.hbm.ntm.weapon.WeaponStatusEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class AberratorBeamEntity extends Projectile {
    public static final double RANGE = 250.0D;
    public static final int LIFETIME = 3;

    private static final EntityDataAccessor<Integer> AMMO =
            SynchedEntityData.defineId(AberratorBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(AberratorBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_LENGTH =
            SynchedEntityData.defineId(AberratorBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DIRECTION =
            SynchedEntityData.defineId(AberratorBeamEntity.class, EntityDataSerializers.VECTOR3);

    public AberratorBeamEntity(EntityType<? extends AberratorBeamEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        noCulling = true;
    }

    public AberratorBeamEntity(ServerLevel level, LivingEntity shooter, AberratorAmmoType ammo,
                               float damage, Vec3 origin, Vec3 heading) {
        this(ModEntities.ABERRATOR_BEAM.get(), level);
        setOwner(shooter);
        entityData.set(AMMO, ammo.legacyMetadata());
        entityData.set(DAMAGE, damage);
        Vec3 direction = heading.normalize();
        entityData.set(DIRECTION,
                new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
        setPos(origin);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AMMO, AberratorAmmoType.V9.legacyMetadata());
        builder.define(DAMAGE, 0.0F);
        builder.define(BEAM_LENGTH, (float) RANGE);
        builder.define(DIRECTION, new Vector3f(0.0F, 0.0F, 1.0F));
    }

    public AberratorAmmoType ammoType() {
        return AberratorAmmoType.fromLegacyMetadata(entityData.get(AMMO));
    }

    public float beamDamage() { return entityData.get(DAMAGE); }
    public float beamLength() { return entityData.get(BEAM_LENGTH); }

    public Vec3 beamDirection() {
        Vector3f direction = entityData.get(DIRECTION);
        Vec3 beam = new Vec3(direction.x, direction.y, direction.z);
        return beam.lengthSqr() > 1.0E-8D ? beam.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    public void performHitscan() {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 start = position();
        Vec3 end = start.add(beamDirection().scale(RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 clippedEnd = blockHit.getType() == HitResult.Type.MISS
                ? end : blockHit.getLocation();
        BeamHit hit = entityHits(level, start, clippedEnd).stream().findFirst().orElse(null);

        if (hit != null) {
            impactEntity(level, hit.entity());
            entityData.set(BEAM_LENGTH, (float) start.distanceTo(hit.location()));
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            impactBlock(level, blockHit);
            entityData.set(BEAM_LENGTH, (float) start.distanceTo(blockHit.getLocation()));
        } else {
            entityData.set(BEAM_LENGTH, (float) RANGE);
        }
    }

    private List<BeamHit> entityHits(ServerLevel level, Vec3 start, Vec3 end) {
        return level.getEntities(this, new AABB(start, end).inflate(1.0D), this::canHit).stream()
                .map(entity -> entity.getBoundingBox().inflate(0.3D).clip(start, end)
                        .map(location -> new BeamHit(entity, location, start.distanceToSqr(location))))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingDouble(BeamHit::distance))
                .toList();
    }

    private boolean canHit(Entity entity) {
        return entity != getOwner() && entity.isAlive() && !entity.isSpectator()
                && entity.isPickable();
    }

    private void impactEntity(ServerLevel level, Entity target) {
        if (ammoType().blackLightning() && target instanceof LivingEntity living) {
            WeaponStatusEvents.addBlackFire(living, 200);
        }
        DamageSource source = level.damageSources().source(ModDamageTypes.BULLET, this, getOwner());
        float damage = target instanceof LivingEntity living
                ? MineExplosion.compensateForArmorPiercing(living, source, beamDamage(),
                        ammoType().armorThresholdNegation(), ammoType().armorPiercing())
                : beamDamage();
        target.invulnerableTime = 0;
        target.hurt(source, damage);
    }

    private void impactBlock(ServerLevel level, BlockHitResult hit) {
        if (!ammoType().blackLightning()) return;
        Vec3 position = hit.getLocation();
        level.addFreshEntity(new LingeringFireEntity(ModEntities.LINGERING_FIRE.get(), level,
                position.x, position.y, position.z, 200, 7.5D, 2.0D,
                LingeringFireEntity.Kind.BLACK));
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > LIFETIME) discard();
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }

    private record BeamHit(Entity entity, Vec3 location, double distance) { }
}
