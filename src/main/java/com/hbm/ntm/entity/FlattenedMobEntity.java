package com.hbm.ntm.entity;

import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** The Naoya result: a mob that used to be an entity and is now a framed transparent rectangle. */
public final class FlattenedMobEntity extends Entity {
    private static final EntityDataAccessor<CompoundTag> VICTIM =
            SynchedEntityData.defineId(FlattenedMobEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> PANE_WIDTH =
            SynchedEntityData.defineId(FlattenedMobEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PANE_HEIGHT =
            SynchedEntityData.defineId(FlattenedMobEntity.class, EntityDataSerializers.FLOAT);

    /** Client display dummy rebuilt from the victim tag; never added to the level or ticked. */
    private Entity displayEntity;
    private CompoundTag displayKey;

    public FlattenedMobEntity(EntityType<? extends FlattenedMobEntity> type, Level level) {
        super(type, level);
    }

    public static FlattenedMobEntity of(ServerLevel level, LivingEntity victim, LivingEntity attacker) {
        FlattenedMobEntity pane = new FlattenedMobEntity(ModEntities.FLATTENED_MOB.get(), level);
        CompoundTag tag = new CompoundTag();
        victim.saveWithoutId(tag);
        tag.putString("id", EntityType.getKey(victim.getType()).toString());
        pane.entityData.set(VICTIM, tag);
        pane.entityData.set(PANE_WIDTH, Math.max(0.5F, victim.getBbWidth()));
        pane.entityData.set(PANE_HEIGHT, Math.max(0.5F, victim.getBbHeight()));
        pane.setPos(victim.getX(), victim.getY(), victim.getZ());
        // The flat face looks back at whoever threw the punch.
        pane.setYRot(attacker.getYRot());
        pane.refreshDimensions();
        return pane;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VICTIM, new CompoundTag());
        builder.define(PANE_WIDTH, 0.5F);
        builder.define(PANE_HEIGHT, 0.5F);
    }

    public String victimTypeId() {
        return entityData.get(VICTIM).getString("id");
    }

    /** Lazily rebuilds the flattened victim for rendering; null when nothing can be restored. */
    public Entity displayEntity() {
        CompoundTag tag = entityData.get(VICTIM);
        if (tag.isEmpty()) return null;
        if (displayEntity == null || displayKey != tag) {
            displayKey = tag;
            displayEntity = EntityType.create(tag, level()).orElse(null);
            if (displayEntity != null) {
                displayEntity.setYRot(0.0F);
                displayEntity.setXRot(0.0F);
                if (displayEntity instanceof LivingEntity living) {
                    // The portrait hangs in its healthiest pose, not mid-flinch or mid-death-flop.
                    living.hurtTime = 0;
                    living.deathTime = 0;
                    living.setHealth(living.getMaxHealth());
                    living.yBodyRot = 0.0F;
                    living.yBodyRotO = 0.0F;
                    living.yHeadRot = 0.0F;
                    living.yHeadRotO = 0.0F;
                }
            }
        }
        return displayEntity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(entityData.get(PANE_WIDTH), entityData.get(PANE_HEIGHT));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (PANE_WIDTH.equals(accessor) || PANE_HEIGHT.equals(accessor)) refreshDimensions();
    }

    @Override
    public void tick() {
        super.tick();
        if (!isNoGravity()) setDeltaMovement(getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().multiply(0.7D, onGround() ? 0.0D : 0.98D, 0.7D));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level() instanceof ServerLevel server && !isRemoved()) shatter(server);
        return true;
    }

    private void shatter(ServerLevel level) {
        discard();
        double centerY = getY() + getBbHeight() * 0.5D;
        level.playSound(null, getX(), centerY, getZ(), SoundEvents.GLASS_BREAK,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()),
                getX(), centerY, getZ(), 20,
                getBbWidth() * 0.5D, getBbHeight() * 0.25D, getBbWidth() * 0.5D, 0.05D);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Victim", entityData.get(VICTIM).copy());
        tag.putFloat("PaneWidth", entityData.get(PANE_WIDTH));
        tag.putFloat("PaneHeight", entityData.get(PANE_HEIGHT));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(VICTIM, tag.getCompound("Victim"));
        entityData.set(PANE_WIDTH, Math.max(0.5F, tag.getFloat("PaneWidth")));
        entityData.set(PANE_HEIGHT, Math.max(0.5F, tag.getFloat("PaneHeight")));
        refreshDimensions();
    }
}
