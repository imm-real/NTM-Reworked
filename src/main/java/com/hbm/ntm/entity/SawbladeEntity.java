package com.hbm.ntm.entity;

import com.hbm.ntm.network.GibletPayload;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Overspeed-thrown Sawmill blade, retaining the old bounce/embed/pickup rules. */
public final class SawbladeEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> ORIENTATION =
            SynchedEntityData.defineId(SawbladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> EMBEDDED =
            SynchedEntityData.defineId(SawbladeEntity.class, EntityDataSerializers.BOOLEAN);

    public SawbladeEntity(EntityType<? extends SawbladeEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ORIENTATION, Direction.NORTH.get3DDataValue());
        builder.define(EMBEDDED, false);
    }

    public void setOrientation(Direction direction) { entityData.set(ORIENTATION, direction.get3DDataValue()); }
    public Direction orientation() { return Direction.from3DDataValue(entityData.get(ORIENTATION)); }
    public boolean embedded() { return entityData.get(EMBEDDED); }

    @Override protected Item getDefaultItem() { return ModItems.SAWBLADE.get(); }
    @Override protected double getDefaultGravity() { return embedded() ? 0.0D : 0.03D; }

    @Override
    public void tick() {
        if (embedded()) {
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(true);
        }
        super.tick();
        if (embedded()) setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        if (!level().isClientSide && target.isAlive()) {
            target.hurt(level().damageSources().source(ModDamageTypes.RUBBLE, this), 1000.0F);
            if (!target.isAlive() && target instanceof LivingEntity && level() instanceof ServerLevel serverLevel) {
                PacketDistributor.sendToPlayersNear(serverLevel, null, target.getX(), target.getY(), target.getZ(),
                        150.0D, new GibletPayload(target.getId()));
                level().playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                        SoundSource.BLOCKS, 2.0F, 0.95F + random.nextFloat() * 0.2F);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide || embedded() || tickCount <= 1) return;

        if (getDeltaMovement().length() < 0.75D) {
            entityData.set(EMBEDDED, true);
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(false);
            return;
        }

        Direction side = result.getDirection();
        Vec3 movement = getDeltaMovement();
        setDeltaMovement(side.getStepX() == 0 ? movement.x : -movement.x,
                side.getStepY() == 0 ? movement.y : -movement.y,
                side.getStepZ() == 0 ? movement.z : -movement.z);
        level().explode(this, getX(), getY(), getZ(), 3.0F, false, Level.ExplosionInteraction.NONE);
        BlockPos position = result.getBlockPos();
        if (level().getBlockState(position).getBlock().getExplosionResistance() < 50.0F) {
            level().destroyBlock(position, false);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide && player.getInventory().add(new ItemStack(ModItems.SAWBLADE.get()))) discard();
        return InteractionResult.PASS;
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return true; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("rot", entityData.get(ORIENTATION));
        tag.putBoolean("embedded", embedded());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ORIENTATION, tag.getInt("rot"));
        entityData.set(EMBEDDED, tag.getBoolean("embedded"));
    }
}
