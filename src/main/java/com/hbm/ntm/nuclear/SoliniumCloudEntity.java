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

/** Blue rinse cloud. Comes with complimentary lightning. */
public final class SoliniumCloudEntity extends Entity {
    // Max age lives fast and dies with the entity data.
    private static final EntityDataAccessor<Integer> MAX_AGE = SynchedEntityData.defineId(
            SoliniumCloudEntity.class, EntityDataSerializers.INT);
    private int age;
    private float scale;

    public SoliniumCloudEntity(EntityType<? extends SoliniumCloudEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public static SoliniumCloudEntity create(ServerLevel level, double x, double y, double z, int maxAge) {
        SoliniumCloudEntity cloud = new SoliniumCloudEntity(ModEntities.SOLINIUM_CLOUD.get(), level);
        cloud.setPos(x, y, z);
        cloud.entityData.set(MAX_AGE, maxAge);
        // No forced frustum for the timed flavor.
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
        // No super tick. It knows what it did.
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
        // One final growth spurt on the way out.
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
        // Reload it and the cloud immediately remembers it has somewhere else to be.
        noCulling = true;
        refreshDimensions();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 25_000.0D;
    }
}
