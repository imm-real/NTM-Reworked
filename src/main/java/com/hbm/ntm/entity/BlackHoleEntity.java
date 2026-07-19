package com.hbm.ntm.entity;

import com.hbm.ntm.radiation.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Destructive singularity used by the B93's ninth charge. */
public class BlackHoleEntity extends Entity {
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(
            BlackHoleEntity.class, EntityDataSerializers.FLOAT);

    public BlackHoleEntity(EntityType<? extends BlackHoleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 0.5F);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public void setSize(float size) {
        entityData.set(SIZE, size);
    }

    @Override
    public void tick() {
        super.tick();
        float size = size();
        if (!level().isClientSide && level() instanceof ServerLevel server) {
            tearTerrain(server, size);
        }
        pullEntities(size);
        setPos(position().add(getDeltaMovement()));
        setDeltaMovement(getDeltaMovement().scale(0.99D));
    }

    private void tearTerrain(ServerLevel level, float size) {
        for (int ray = 0; ray < size * 2.0F; ray++) {
            double phi = random.nextDouble() * Mth.TWO_PI;
            double cosTheta = random.nextDouble() * 2.0D - 1.0D;
            double theta = Math.acos(cosTheta);
            Vec3 direction = new Vec3(
                    Math.sin(theta) * Math.cos(phi),
                    Math.sin(theta) * Math.sin(phi),
                    Math.cos(theta));
            int length = Mth.ceil(size * 15.0F);
            for (int step = 0; step < length; step++) {
                // Java casts, not floor coordinates. Negative space notices the difference.
                BlockPos pos = new BlockPos((int) (getX() + direction.x * step),
                        (int) (getY() + direction.y * step),
                        (int) (getZ() + direction.z * step));
                if (!level.isInWorldBounds(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (!state.getFluidState().isEmpty()) {
                    level.removeBlock(pos, false);
                    state = level.getBlockState(pos);
                }
                if (state.isAir()) continue;
                FallingBlockEntity rubble = FallingBlockEntity.fall(level, pos, state);
                rubble.dropItem = false;
                rubble.disableDrop();
                break;
            }
        }
    }

    private void pullEntities(float size) {
        double range = size * 15.0D;
        List<Entity> entities = level().getEntities(this, new AABB(position(), position()).inflate(range));
        for (Entity entity : entities) {
            if (entity instanceof Player player && player.isCreative()) continue;
            Vec3 pull = position().subtract(entity.position());
            double distance = pull.length();
            if (distance <= 0.0D || distance > range) continue;
            pull = pull.normalize();
            if (!(entity instanceof ItemEntity)) pull = pull.yRot(15.0F * Mth.DEG_TO_RAD);
            entity.setDeltaMovement(entity.getDeltaMovement().add(
                    pull.x * 0.1D, pull.y * 0.2D, pull.z * 0.1D));
            if (entity instanceof BlackHoleEntity) continue;
            if (distance < size * 1.5D) {
                entity.hurt(damageSources().source(ModDamageTypes.BLACK_HOLE, this), 1_000.0F);
                if (!(entity instanceof LivingEntity)) entity.discard();
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("size", size());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setSize(tag.getFloat("size"));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 25_000.0D;
    }
}
