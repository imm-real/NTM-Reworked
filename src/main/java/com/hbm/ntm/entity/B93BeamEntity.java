package com.hbm.ntm.entity;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.item.B92Item;
import com.hbm.ntm.nuclear.NuclearExplosionEntity;
import com.hbm.ntm.registry.ModEffects;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** Ten-mode red/blue projectile fired by the historical B93 Energy Mod. */
public final class B93BeamEntity extends Projectile {
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(
            B93BeamEntity.class, EntityDataSerializers.INT);
    private BlockPos pendingBlock;
    private int ticksInAir;
    private int arrowShake;

    public B93BeamEntity(EntityType<? extends B93BeamEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public B93BeamEntity(ServerLevel level, LivingEntity shooter) {
        this(ModEntities.B93_BEAM.get(), level);
        setOwner(shooter);
        setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        setYRot(shooter.getYRot());
        setXRot(shooter.getXRot());
        double yaw = shooter.getYRot() * Mth.DEG_TO_RAD;
        setPos(getX() - Mth.cos((float) yaw) * 0.16D, getY(), getZ() - Mth.sin((float) yaw) * 0.16D);
        Vec3 heading = shooter.getLookAngle();
        double x = heading.x + random.nextGaussian() * (random.nextBoolean() ? -1.0D : 1.0D) * 0.002499999832361937D;
        double y = heading.y + random.nextGaussian() * (random.nextBoolean() ? -1.0D : 1.0D) * 0.002499999832361937D;
        double z = heading.z + random.nextGaussian() * (random.nextBoolean() ? -1.0D : 1.0D) * 0.002499999832361937D;
        Vec3 movement = new Vec3(x, y, z).scale(4.5D);
        setDeltaMovement(movement);
        setYRot((float) (Mth.atan2(movement.x, movement.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(movement.y, Math.hypot(movement.x, movement.z)) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODE, 0);
    }

    public int mode() { return entityData.get(MODE); }
    public void setMode(int mode) { entityData.set(MODE, mode); }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 100) discard();
        if (level().isClientSide) {
            setPos(position().add(getDeltaMovement()));
            setYRot((float) (Mth.atan2(getDeltaMovement().x, getDeltaMovement().z) * Mth.RAD_TO_DEG));
            return;
        }
        ServerLevel server = (ServerLevel) level();
        if (pendingBlock != null && !server.getBlockState(pendingBlock).isAir()) {
            discard();
            impact(server, mode(), getX(), getY(), getZ());
        }
        if (arrowShake > 0) {
            arrowShake--;
            return;
        }

        ticksInAir++;
        Vec3 start = position();
        Vec3 movement = getDeltaMovement();
        Vec3 end = start.add(movement);
        BlockHitResult blockHit = server.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 entityEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        EntityIntersection nearest = null;
        AABB sweep = getBoundingBox().expandTowards(movement).inflate(1.0D);
        List<Entity> candidates = server.getEntities(this, sweep, this::canCollide);
        for (Entity candidate : candidates) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(start, entityEnd);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (nearest == null || distance < nearest.distanceSqr) nearest = new EntityIntersection(candidate, distance);
        }

        if (nearest != null) {
            if (allowedPlayerImpact(nearest.entity)) {
                if (nearest.entity instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(ModEffects.BANG, 60, 0));
                } else {
                    impact(server, mode(), getX(), getY(), getZ());
                }
                discard();
            }
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            pendingBlock = blockHit.getBlockPos();
        }

        boolean wasInWater = isInWater();
        setPos(end);
        setYRot((float) (Mth.atan2(movement.x, movement.z) * Mth.RAD_TO_DEG));
        if (wasInWater) {
            discard();
            impact(server, mode(), getX(), getY(), getZ());
        }
        if (isInWaterRainOrBubble()) clearFire();
        checkInsideBlocks();
    }

    private boolean canCollide(Entity entity) {
        if (!entity.isAlive() || !entity.isPickable()) return false;
        Entity owner = getOwner();
        return entity != owner || ticksInAir >= 5;
    }

    private boolean allowedPlayerImpact(Entity entity) {
        if (!(entity instanceof Player target)) return true;
        if (target.getAbilities().invulnerable) return false;
        return !(getOwner() instanceof Player shooter) || shooter.canHarmPlayer(target);
    }

    public static void impact(ServerLevel level, int mode, double x, double y, double z) {
        switch (mode) {
            case 0 -> level.explode(null, x, y, z, 5.0F, false, Level.ExplosionInteraction.TNT);
            case 1 -> level.explode(null, x, y, z, 10.0F, true, Level.ExplosionInteraction.TNT);
            case 2 -> B92Item.spawnFleija(level, x, y, z, 10, 10);
            case 3 -> B92Item.spawnFleija(level, x, y, z, 20, 20);
            case 4 -> spawnVortex(level, ModEntities.VORTEX.get().create(level), 1.0F, x, y, z);
            case 5 -> spawnVortex(level, ModEntities.VORTEX.get().create(level), 2.5F, x, y, z);
            case 6 -> spawnVortex(level, ModEntities.RAGING_VORTEX.get().create(level), 2.5F, x, y, z);
            case 7 -> spawnVortex(level, ModEntities.RAGING_VORTEX.get().create(level), 5.0F, x, y, z);
            case 8 -> spawnVortex(level, ModEntities.BLACK_HOLE.get().create(level), 2.0F, x, y, z);
            default -> NuclearExplosionEntity.spawnLargeNuke(
                    level, x, y, z, HbmConfig.GADGET_RADIUS.get(), false);
        }
    }

    private static void spawnVortex(ServerLevel level, BlackHoleEntity vortex,
                                    float size, double x, double y, double z) {
        if (vortex == null) return;
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 100.0F, 0.9F + level.random.nextFloat() * 0.1F);
        vortex.setSize(size);
        vortex.setPos(x, y, z);
        level.addFreshEntity(vortex);
    }

    public BlockPos pendingBlock() { return pendingBlock; }
    public int ticksInAir() { return ticksInAir; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        BlockPos saved = pendingBlock == null ? new BlockPos(-1, -1, -1) : pendingBlock;
        tag.putShort("xTile", (short) saved.getX());
        tag.putShort("yTile", (short) saved.getY());
        tag.putShort("zTile", (short) saved.getZ());
        tag.putShort("life", (short) 0);
        tag.putByte("shake", (byte) arrowShake);
        tag.putInt("mode", mode());
        tag.putDouble("damage", 2.0D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        int x = tag.getShort("xTile");
        int y = tag.getShort("yTile");
        int z = tag.getShort("zTile");
        pendingBlock = x == -1 && y == -1 && z == -1 ? null : new BlockPos(x, y, z);
        arrowShake = tag.getByte("shake") & 255;
        setMode(tag.getInt("mode"));
        ticksInAir = 0;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 102_400.0D;
    }

    private record EntityIntersection(Entity entity, double distanceSqr) { }
}
