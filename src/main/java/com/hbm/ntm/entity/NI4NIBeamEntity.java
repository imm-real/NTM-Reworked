package com.hbm.ntm.entity;

import com.hbm.ntm.explosion.MineExplosion;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class NI4NIBeamEntity extends Projectile {
    public static final double RANGE = 250.0D;
    public static final double RICOCHET_SEARCH_RANGE = 50.0D;
    public static final int LIFETIME = 5;
    public static final float RICOCHET_MULTIPLIER = 1.25F;
    public static final float ARMOR_THRESHOLD_NEGATION = 10.0F;
    public static final float ARMOR_PIERCING = 0.2F;

    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(NI4NIBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_LENGTH =
            SynchedEntityData.defineId(NI4NIBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DIRECTION =
            SynchedEntityData.defineId(NI4NIBeamEntity.class, EntityDataSerializers.VECTOR3);

    public NI4NIBeamEntity(EntityType<? extends NI4NIBeamEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public NI4NIBeamEntity(ServerLevel level, LivingEntity shooter, float damage, Vec3 localOffset) {
        this(ModEntities.NI4NI_BEAM.get(), level);
        setOwner(shooter);
        entityData.set(DAMAGE, damage);
        point(shooter.getLookAngle());
        float yaw = shooter.getYRot();
        float pitch = shooter.getXRot();
        setPos(shooter.getEyePosition().add(localOffset.xRot(-pitch * Mth.DEG_TO_RAD)
                .yRot(-yaw * Mth.DEG_TO_RAD)));
    }

    private NI4NIBeamEntity(ServerLevel level, Entity owner, float damage, Vec3 origin, Vec3 direction) {
        this(ModEntities.NI4NI_BEAM.get(), level);
        setOwner(owner);
        entityData.set(DAMAGE, damage);
        setPos(origin);
        point(direction);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 0.0F);
        builder.define(BEAM_LENGTH, (float) RANGE);
        builder.define(DIRECTION, new Vector3f(0.0F, 0.0F, 1.0F));
    }

    private void point(Vec3 direction) {
        Vec3 normalized = direction.normalize();
        entityData.set(DIRECTION, new Vector3f(
                (float) normalized.x, (float) normalized.y, (float) normalized.z));
        setYRot((float) (Mth.atan2(normalized.x, normalized.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(-normalized.y,
                Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z)) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    public float beamDamage() { return entityData.get(DAMAGE); }
    public float beamLength() { return entityData.get(BEAM_LENGTH); }

    public Vec3 beamDirection() {
        Vector3f direction = entityData.get(DIRECTION);
        Vec3 beam = new Vec3(direction.x, direction.y, direction.z);
        return beam.lengthSqr() > 1.0E-8D ? beam.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    public void performHitscan() {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 start = position();
        Vec3 end = start.add(beamDirection().scale(RANGE));
        BlockHitResult blockHit = server.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        Vec3 clippedEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        BeamHit coinHit = nearestHit(server, start, clippedEnd, entity -> entity instanceof NI4NICoinEntity);
        if (coinHit != null) {
            entityData.set(BEAM_LENGTH, (float) start.distanceTo(coinHit.location()));
            ricochet(server, (NI4NICoinEntity) coinHit.entity(), coinHit.location());
            return;
        }

        BeamHit hit = nearestHit(server, start, clippedEnd,
                entity -> !(entity instanceof NI4NICoinEntity) && canHit(entity));
        if (hit != null) {
            impactEntity(server, hit.entity());
            entityData.set(BEAM_LENGTH, (float) start.distanceTo(hit.location()));
        } else {
            entityData.set(BEAM_LENGTH, (float) start.distanceTo(clippedEnd));
        }
    }

    private void ricochet(ServerLevel server, NI4NICoinEntity coin, Vec3 hit) {
        Entity nextOwner = coin.getOwner() != null ? coin.getOwner() : getOwner();
        coin.discard();
        server.sendParticles(ParticleTypes.EXPLOSION, hit.x, hit.y, hit.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);

        Entity target = nearestRicochetTarget(server, hit, nextOwner);
        Vec3 direction = target != null
                ? target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(hit)
                : new Vec3(random.nextGaussian() * 0.5D, -1.0D, random.nextGaussian() * 0.5D);
        NI4NIBeamEntity next = new NI4NIBeamEntity(server, nextOwner,
                beamDamage() * RICOCHET_MULTIPLIER, hit, direction);
        next.performHitscan();
        server.addFreshEntity(next);
    }

    private Entity nearestRicochetTarget(ServerLevel server, Vec3 origin, Entity owner) {
        AABB area = new AABB(origin, origin).inflate(RICOCHET_SEARCH_RANGE);
        List<Entity> nearby = server.getEntities(this, area,
                entity -> entity != owner && entity.isAlive() && !entity.isSpectator());
        Comparator<Entity> distance = Comparator.comparingDouble(entity -> entity.distanceToSqr(origin));
        Optional<Entity> target = nearby.stream().filter(NI4NICoinEntity.class::isInstance).min(distance);
        if (target.isEmpty()) target = nearby.stream().filter(Player.class::isInstance).min(distance);
        if (target.isEmpty()) target = nearby.stream().filter(Monster.class::isInstance).min(distance);
        if (target.isEmpty()) target = nearby.stream().filter(LivingEntity.class::isInstance).min(distance);
        return target.orElse(null);
    }

    private BeamHit nearestHit(ServerLevel server, Vec3 start, Vec3 end, Predicate<Entity> filter) {
        List<BeamHit> hits = new ArrayList<>();
        AABB sweep = new AABB(start, end).inflate(1.0D);
        for (Entity candidate : server.getEntities(this, sweep, filter)) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
            hit.ifPresent(location -> hits.add(new BeamHit(
                    candidate, location, start.distanceToSqr(location))));
        }
        return hits.stream().min(Comparator.comparingDouble(BeamHit::distance)).orElse(null);
    }

    private boolean canHit(Entity entity) {
        return entity != getOwner() && entity.isAlive() && !entity.isSpectator() && entity.isPickable();
    }

    private void impactEntity(ServerLevel server, Entity target) {
        target.invulnerableTime = 0;
        var source = server.damageSources().source(ModDamageTypes.BULLET, this, getOwner());
        Vec3 motion = target.getDeltaMovement();
        float damage = target instanceof LivingEntity living
                ? MineExplosion.compensateForArmorPiercing(living, source, beamDamage(),
                        ARMOR_THRESHOLD_NEGATION, ARMOR_PIERCING)
                : beamDamage();
        target.hurt(source, damage);
        target.setDeltaMovement(motion);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount >= LIFETIME) discard();
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }

    private record BeamHit(Entity entity, Vec3 location, double distance) { }
}
