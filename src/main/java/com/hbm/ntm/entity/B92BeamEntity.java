package com.hbm.ntm.entity;

import com.hbm.ntm.nuclear.FleijaExplosionEntity;
import com.hbm.ntm.nuclear.FleijaRainbowCloudEntity;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import java.util.Random;

/** B92 beam. Explodes now, stops later. */
public final class B92BeamEntity extends Projectile {
    private BlockPos pendingBlock;
    private int ticksInAir;
    private int arrowShake;

    public B92BeamEntity(EntityType<? extends B92BeamEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public B92BeamEntity(ServerLevel level, LivingEntity shooter) {
        this(ModEntities.B92_BEAM.get(), level);
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

    public void addDivergence(Random random, double multiplier) {
        setDeltaMovement(getDeltaMovement().add(
                random.nextGaussian() * multiplier,
                random.nextGaussian() * multiplier,
                random.nextGaussian() * multiplier));
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { }

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
            explode(server);
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
            if (nearest == null || distance < nearest.distanceSqr) {
                nearest = new EntityIntersection(candidate, distance);
            }
        }

        if (nearest != null) {
            // Entity wins over wall. An invalid player hit erases both. Very legal.
            if (allowedPlayerImpact(nearest.entity)) explode(server);
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            pendingBlock = blockHit.getBlockPos();
        }

        boolean wasInWater = isInWater();
        setPos(end);
        setYRot((float) (Mth.atan2(movement.x, movement.z) * Mth.RAD_TO_DEG));
        if (wasInWater) {
            discard();
            explode(server);
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

    private void explode(ServerLevel level) {
        FleijaExplosionEntity explosion = FleijaExplosionEntity.create(level, getX(), getY(), getZ(), 10);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 100.0F, 0.9F + random.nextFloat() * 0.1F);
        level.addFreshEntity(explosion);
        level.addFreshEntity(FleijaRainbowCloudEntity.create(level, getX(), getY(), getZ(), 10));
    }

    public BlockPos pendingBlock() { return pendingBlock; }
    public int ticksInAir() { return ticksInAir; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        BlockPos saved = pendingBlock == null ? new BlockPos(-1, -1, -1) : pendingBlock;
        // Signed shorts: for when coordinates deserve gambling.
        tag.putShort("xTile", (short) saved.getX());
        tag.putShort("yTile", (short) saved.getY());
        tag.putShort("zTile", (short) saved.getZ());
        tag.putShort("life", (short) 0);
        tag.putByte("inTile", (byte) 0);
        tag.putByte("inData", (byte) 0);
        tag.putByte("shake", (byte) arrowShake);
        tag.putByte("inGround", (byte) 0);
        tag.putByte("pickup", (byte) 0);
        tag.putDouble("damage", 2.0D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("xTile")) {
            int x = tag.getShort("xTile");
            int y = tag.getShort("yTile");
            int z = tag.getShort("zTile");
            pendingBlock = x == -1 && y == -1 && z == -1 ? null : new BlockPos(x, y, z);
        } else if (tag.contains("pendingBlock")) {
            // Archaeology from the unreleased port.
            pendingBlock = BlockPos.of(tag.getLong("pendingBlock"));
        }
        arrowShake = tag.getByte("shake") & 255;
        // Reloading restores five ticks of owner immunity. Save-scumming, but for lasers.
        ticksInAir = 0;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // Tiny beam, enormous ego, 320-block render distance.
        return distance < 102_400.0D;
    }

    private record EntityIntersection(Entity entity, double distanceSqr) { }
}
