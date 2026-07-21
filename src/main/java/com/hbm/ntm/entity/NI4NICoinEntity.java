package com.hbm.ntm.entity;

import com.hbm.ntm.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class NI4NICoinEntity extends Projectile {
    public static final double GRAVITY = 0.02D;

    public NI4NICoinEntity(EntityType<? extends NI4NICoinEntity> type, Level level) {
        super(type, level);
    }

    public NI4NICoinEntity(ServerLevel level, Entity owner, Vec3 origin, Vec3 velocity) {
        this(ModEntities.NI4NI_COIN.get(), level);
        setOwner(owner);
        setPos(origin);
        setDeltaMovement(velocity);
        setYRot(owner.getYRot());
        yRotO = getYRot();
    }

    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { }

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = getDeltaMovement();
        Vec3 start = position();
        Vec3 end = start.add(velocity);
        BlockHitResult hit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            setPos(hit.getLocation());
            discard();
            return;
        }

        setPos(end);
        double drag = isInWater() ? 0.8D : 1.0D;
        setDeltaMovement(velocity.scale(drag).add(0.0D, -GRAVITY, 0.0D));
        updateRotation();
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isAttackable() { return true; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
