package com.hbm.ntm.entity;

import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModParticles;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The falling building created by Subtlety's hidden .50 BMG Demolisher round.
 * This directly follows {@code EntityBuilding}: it appears 50 blocks above the
 * bullet impact, accelerates by 0.03 blocks/tick to a 1.5 terminal speed, and
 * bursts into brick rubble on touching any non-air block.
 */
public final class BuildingEntity extends Projectile {
    public static final double SPAWN_HEIGHT = 50.0D;
    public static final double GRAVITY = 0.03D;
    public static final double TERMINAL_VELOCITY = -1.5D;
    public static final int IMPACT_DAMAGE = 1_000;
    public static final int RUBBLE_COUNT = 250;

    public BuildingEntity(EntityType<? extends BuildingEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** Source {@code XFactory50.LAMBDA_BUILDING}. */
    public static BuildingEntity spawn(ServerLevel level, Vec3 impact) {
        BuildingEntity building = new BuildingEntity(ModEntities.BUILDING.get(), level);
        building.setPos(impact.x, impact.y + SPAWN_HEIGHT, impact.z);
        level.addFreshEntity(building);
        return building;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        ServerLevel level = (ServerLevel) level();
        if (tickCount == 1) spawnArrivalCloud(level);

        Vec3 movement = getDeltaMovement();
        setPos(getX() + movement.x, getY() + movement.y, getZ() + movement.z);
        setDeltaMovement(movement.x, Math.max(TERMINAL_VELOCITY, movement.y - GRAVITY), movement.z);

        // EntityBuilding used integer casts rather than floor when sampling the block.
        BlockPos sample = new BlockPos((int) getX(), (int) getY(), (int) getZ());
        if (!level.getBlockState(sample).isAir()) detonate(level);
    }

    private void spawnArrivalCloud(ServerLevel level) {
        for (int i = 0; i < 100; i++) {
            level.sendParticles(ModParticles.FLAMETHROWER_BALEFIRE.get(),
                    getX() + (random.nextDouble() - 0.5D) * 15.0D,
                    getY() + (random.nextDouble() - 0.5D) * 15.0D,
                    getZ() + (random.nextDouble() - 0.5D) * 15.0D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void detonate(ServerLevel level) {
        level.playSound(null, getX(), getY(), getZ(), ModSounds.BUILDING_EXPLOSION.get(),
                SoundSource.AMBIENT, 10_000.0F, 0.5F + random.nextFloat() * 0.1F);

        spawnCloud(level, 150);
        spawnShock(level, 24, 6.0D);
        spawnShock(level, 24, 5.0D);
        spawnShock(level, 24, 4.0D);
        spawnShock(level, 24, 3.0D);
        spawnShock(level, 24, 3.0D);

        AABB damageArea = new AABB(getX() - 8.0D, getY() - 8.0D, getZ() - 8.0D,
                getX() + 8.0D, getY() + 8.0D, getZ() + 8.0D);
        for (Entity entity : level.getEntities(this, damageArea, Entity::isAlive)) {
            entity.hurt(level.damageSources().source(ModDamageTypes.BUILDING, this), IMPACT_DAMAGE);
        }

        for (int i = 0; i < RUBBLE_COUNT; i++) {
            double elevation = random.nextDouble() * Math.PI * 0.5D;
            double yaw = random.nextDouble() * Math.PI * 2.0D;
            double horizontal = Math.cos(elevation);
            PowerFistRubbleEntity rubble = new PowerFistRubbleEntity(ModEntities.POWER_FIST_RUBBLE.get(), level);
            rubble.setPos(getX(), getY() + 3.0D, getZ());
            rubble.setBlockState(Blocks.BRICKS.defaultBlockState());
            rubble.setDeltaMovement(horizontal * Math.cos(yaw), Math.sin(elevation),
                    -horizontal * Math.sin(yaw));
            level.addFreshEntity(rubble);
        }

        discard();
    }

    /** Source cloud mode from {@code ExplosionLarge.spawnParticles(..., 150)}. */
    private void spawnCloud(ServerLevel level, int count) {
        for (int i = 0; i < count; i++) {
            double y = random.nextGaussian() * (1 + count / 100);
            if (random.nextBoolean()) y = Math.abs(y);
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 3.0D, getZ(), 0,
                    random.nextGaussian() * (1 + count / 150), y,
                    random.nextGaussian() * (1 + count / 150), 1.0D);
        }
    }

    /** Source shock mode: 24 evenly spaced horizontal smoke particles per ring. */
    private void spawnShock(ServerLevel level, int count, double strength) {
        double start = random.nextInt(360);
        for (int i = 0; i < count; i++) {
            double angle = start + Math.PI * 2.0D * i / count;
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.5D, getZ(), 0,
                    Math.cos(angle) * strength, 0.0D, -Math.sin(angle) * strength, 1.0D);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 25_000.0D;
    }
}
