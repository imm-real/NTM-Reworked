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
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

/** The cyan screaming ball. Lightning sold separately. */
public final class FleijaCloudEntity extends Entity {
    // Max age was never saved. Neither is your house.
    private static final EntityDataAccessor<Integer> MAX_AGE = SynchedEntityData.defineId(
            FleijaCloudEntity.class, EntityDataSerializers.INT);
    private int age;
    private float scale;

    public FleijaCloudEntity(EntityType<? extends FleijaCloudEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public static FleijaCloudEntity create(ServerLevel level, double x, double y, double z, int maxAge) {
        FleijaCloudEntity cloud = new FleijaCloudEntity(ModEntities.FLEIJA_CLOUD.get(), level);
        cloud.setPos(x, y, z);
        cloud.entityData.set(MAX_AGE, maxAge);
        // Timed clouds are allowed to lose hide-and-seek.
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
        // No super tick. FLEIJA answers to nobody.
        age++;
        // Flash the sky without summoning Zeus.
        level().setSkyFlashTime(2);
        if (!level().isClientSide && age >= maxAge()) {
            age = 0;
            discard();
        }
        // It grows once more while dying, for dramatic effect.
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
        // Reloaded clouds forgot their birthday and die next tick.
        noCulling = true;
        refreshDimensions();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 25_000.0D;
    }
}
