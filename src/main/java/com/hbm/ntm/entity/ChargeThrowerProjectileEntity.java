package com.hbm.ntm.entity;

import com.hbm.ntm.explosion.MineExplosion;
import com.hbm.ntm.network.MineBlastPayload;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.weapon.ChargeThrowerAmmoType;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

public final class ChargeThrowerProjectileEntity extends Projectile {
    private static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(
            ChargeThrowerProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(
            ChargeThrowerProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    public ChargeThrowerProjectileEntity(EntityType<? extends ChargeThrowerProjectileEntity> type,
                                         Level level) {
        super(type, level);
    }

    public ChargeThrowerProjectileEntity(ServerLevel level, LivingEntity owner,
                                         ChargeThrowerAmmoType ammo, Vec3 origin, Vec3 heading) {
        this(ModEntities.CHARGE_THROWER_PROJECTILE.get(), level);
        setOwner(owner);
        entityData.set(AMMO, ammo.legacyMetadata());
        setPos(origin);
        Vec3 motion = heading.normalize().scale(ChargeThrowerAmmoType.SPEED);
        setDeltaMovement(motion);
        updateRotation(motion);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AMMO, ChargeThrowerAmmoType.MORTAR.legacyMetadata());
        builder.define(STUCK, false);
    }

    public ChargeThrowerAmmoType ammoType() {
        return ChargeThrowerAmmoType.fromLegacyMetadata(entityData.get(AMMO));
    }

    public boolean stuck() { return entityData.get(STUCK); }

    @Override
    public void tick() {
        super.tick();
        ChargeThrowerAmmoType ammo = ammoType();
        if (tickCount > ammo.projectileLifetime()) {
            discard();
            return;
        }
        if (stuck()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 start = position();
        Vec3 step = getDeltaMovement();
        Vec3 end = start.add(step);
        if (!level().isClientSide) {
            BlockHitResult blockHit = level().clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 collisionEnd = blockHit.getType() == HitResult.Type.MISS
                    ? end : blockHit.getLocation();
            if (ammo.kind() != ChargeThrowerAmmoType.Kind.HOOK) {
                EntityHit nearest = nearestEntity(start, collisionEnd, step);
                if (nearest != null) {
                    impact(nearest.location());
                    return;
                }
            }
            if (blockHit.getType() != HitResult.Type.MISS) {
                if (ammo.kind() == ChargeThrowerAmmoType.Kind.HOOK) stick(blockHit, step);
                else impact(blockHit.getLocation());
                return;
            }
        }

        setPos(end);
        Vec3 next = step.add(0.0D, -ammo.gravity(), 0.0D);
        setDeltaMovement(next);
        updateRotation(next);
    }

    private EntityHit nearestEntity(Vec3 start, Vec3 end, Vec3 step) {
        AABB sweep = getBoundingBox().expandTowards(step).inflate(0.3D);
        Vec3 nearest = null;
        double distance = Double.MAX_VALUE;
        for (Entity candidate : level().getEntities(this, sweep, this::canHit)) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
            if (hit.isEmpty()) continue;
            double current = start.distanceToSqr(hit.get());
            if (current < distance) {
                distance = current;
                nearest = hit.get();
            }
        }
        return nearest == null ? null : new EntityHit(nearest);
    }

    private boolean canHit(Entity entity) {
        return entity.isAlive() && !entity.isSpectator() && entity.isPickable()
                && (entity != getOwner() || tickCount >= 3);
    }

    private void stick(BlockHitResult hit, Vec3 motion) {
        Vec3 back = motion.lengthSqr() < 1.0E-8D ? Vec3.ZERO : motion.normalize().scale(-0.05D);
        setPos(hit.getLocation().add(back));
        setDeltaMovement(Vec3.ZERO);
        entityData.set(STUCK, true);
        noPhysics = true;
    }

    private void impact(Vec3 hit) {
        if (!(level() instanceof ServerLevel level)) return;
        ChargeThrowerAmmoType ammo = ammoType();
        float radius = ammo.kind() == ChargeThrowerAmmoType.Kind.CHARGED_MORTAR ? 15.0F : 5.0F;
        int resolution = ammo.kind() == ChargeThrowerAmmoType.Kind.CHARGED_MORTAR ? 48 : 32;
        MineExplosion.blastEntities(level, hit.x, hit.y, hit.z, radius, ammo.damage(),
                1.0D, 0.0F, 0.0F, 1.0F, this, getOwner());
        MineExplosion.blastBlocks(level, hit.x, hit.y, hit.z, radius, resolution, false, getOwner());
        PacketDistributor.sendToPlayersNear(level, null, hit.x, hit.y, hit.z, 200.0D,
                new MineBlastPayload(hit.x, hit.y, hit.z, 10,
                        ammo.kind() == ChargeThrowerAmmoType.Kind.CHARGED_MORTAR ? 2.5F : 1.0F));
        setPos(hit);
        discard();
    }

    private void updateRotation(Vec3 motion) {
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        setYRot((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(motion.y, horizontal) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(AMMO, tag.getInt("Ammo"));
        entityData.set(STUCK, tag.getBoolean("Stuck"));
        noPhysics = stuck();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Ammo", entityData.get(AMMO));
        tag.putBoolean("Stuck", stuck());
    }

    private record EntityHit(Vec3 location) { }
}
