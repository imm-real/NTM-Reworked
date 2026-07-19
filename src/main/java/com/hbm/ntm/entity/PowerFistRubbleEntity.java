package com.hbm.ntm.entity;

import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A textured chunk of terrain thrown by the Power Fist's heavy modes. */
public final class PowerFistRubbleEntity extends Projectile {
    private static final EntityDataAccessor<BlockState> BLOCK_STATE =
            SynchedEntityData.defineId(PowerFistRubbleEntity.class, EntityDataSerializers.BLOCK_STATE);

    public PowerFistRubbleEntity(EntityType<? extends PowerFistRubbleEntity> type, Level level) {
        super(type, level);
    }

    public static PowerFistRubbleEntity create(ServerLevel level, BlockPos position,
                                                BlockState state, double upwardVelocity) {
        PowerFistRubbleEntity rubble = new PowerFistRubbleEntity(ModEntities.POWER_FIST_RUBBLE.get(), level);
        rubble.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        rubble.setBlockState(state);
        rubble.setDeltaMovement(0.0D, upwardVelocity, 0.0D);
        return rubble;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLOCK_STATE, Blocks.AIR.defaultBlockState());
    }

    public BlockState blockState() {
        return entityData.get(BLOCK_STATE);
    }

    public void setBlockState(BlockState state) {
        entityData.set(BLOCK_STATE, state == null ? Blocks.AIR.defaultBlockState() : state);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 movement = getDeltaMovement();

        if (level().isClientSide) {
            BlockHitResult blockHit = level().clip(new ClipContext(
                    position(), position().add(movement), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this));
            if (blockHit.getType() != HitResult.Type.MISS && tickCount > 2) discard();
        } else {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canStrike);
            if (hit instanceof EntityHitResult entityHit) {
                entityHit.getEntity().hurt(
                        level().damageSources().source(ModDamageTypes.RUBBLE), 15.0F);
            }
            if (hit.getType() != HitResult.Type.MISS && tickCount > 2) {
                breakApart((ServerLevel) level());
            }
        }

        setPos(position().add(movement));
        double drag = isInWater() ? 0.8D : 1.0D;
        if (level().isClientSide && isInWater()) {
            for (int index = 0; index < 4; index++) {
                level().addParticle(ParticleTypes.BUBBLE,
                        getX() - movement.x * 0.25D,
                        getY() - movement.y * 0.25D,
                        getZ() - movement.z * 0.25D,
                        movement.x, movement.y, movement.z);
            }
        }
        setDeltaMovement(movement.x * drag, movement.y * drag - 0.03D, movement.z * drag);
        updateRotation();
        checkInsideBlocks();
    }

    private boolean canStrike(Entity entity) {
        if (!entity.isAlive() || !entity.isPickable()) return false;
        Entity owner = getOwner();
        return entity != owner || tickCount >= 5;
    }

    private void breakApart(ServerLevel level) {
        discard();
        level.playSound(null, getX(), getY(), getZ(), ModSounds.DEBRIS.get(),
                SoundSource.BLOCKS, 1.5F, 1.0F);
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, blockPosition(), Block.getId(blockState()));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("BlockState", NbtUtils.writeBlockState(blockState()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setBlockState(NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK),
                tag.getCompound("BlockState")));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // EntityThrowableNT used bounding-box size * 4 * 64: 0.25 * 4 * 64 = 64.
        return distance < 4_096.0D;
    }
}
