package com.hbm.ntm.nuclear;

import com.hbm.ntm.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

/** Weaponized rainbow friendship. */
public final class FleijaRainbowCloudEntity extends Entity {
    private static final EntityDataAccessor<Integer> MAX_AGE = SynchedEntityData.defineId(
            FleijaRainbowCloudEntity.class, EntityDataSerializers.INT);
    private int age;
    private float scale;

    public FleijaRainbowCloudEntity(EntityType<? extends FleijaRainbowCloudEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public static FleijaRainbowCloudEntity create(ServerLevel level, double x, double y, double z, int maxAge) {
        FleijaRainbowCloudEntity cloud = new FleijaRainbowCloudEntity(ModEntities.FLEIJA_RAINBOW_CLOUD.get(), level);
        cloud.setPos(x, y, z);
        cloud.entityData.set(MAX_AGE, maxAge);
        // Timed rainbows may hide behind the frustum.
        cloud.noCulling = false;
        cloud.refreshDimensions();
        return cloud;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MAX_AGE, 0);
    }

    @Override
    public void tick() {
        // No super tick. Friendship needs no supervision.
        age++;
        if (!level().isClientSide && level() instanceof ServerLevel server) {
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(server);
            if (lightning != null) {
                lightning.setPos(getX(), getY() + 200.0D, getZ());
                server.addFreshEntity(lightning);
            }
        }
        if (age >= maxAge()) {
            age = 0;
            discard();
        }
        // One last rainbow expansion before death.
        scale++;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return maxAge() > 0 ? EntityDimensions.scalable(20.0F, 40.0F)
                : EntityDimensions.scalable(1.0F, 4.0F);
    }

    public int age() { return age; }
    public float scale() { return scale; }
    public int maxAge() { return entityData.get(MAX_AGE); }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putShort("age", (short) age);
        tag.putShort("scale", (short) scale);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getShort("age");
        scale = tag.getShort("scale");
        // Reloaded rainbows forget their age and immediately retire.
        noCulling = true;
        refreshDimensions();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 25_000.0D;
    }
}
