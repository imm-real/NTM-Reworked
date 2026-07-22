package com.hbm.ntm.entity;

import com.hbm.ntm.block.FluidStorageTankBlock;
import com.hbm.ntm.blockentity.FluidStorageTankBlockEntity;
import com.hbm.ntm.blockentity.FluidStorageTankProxyBlockEntity;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.weapon.FireExtinguisherAmmoType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Invisible source-velocity droplet carrying water, foam, or boron sand. */
public final class FireExtinguisherProjectileEntity extends Projectile {
    public static final int LIFE_TICKS = 100;
    public static final float SPEED = 0.75F;
    public static final float GRAVITY = 0.04F;

    private static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(
            FireExtinguisherProjectileEntity.class, EntityDataSerializers.INT);

    public FireExtinguisherProjectileEntity(EntityType<? extends FireExtinguisherProjectileEntity> type,
                                            Level level) {
        super(type, level);
        noPhysics = true;
    }

    public FireExtinguisherProjectileEntity(ServerLevel level, LivingEntity owner,
                                            FireExtinguisherAmmoType type,
                                            float spread, Vec3 origin, Vec3 heading) {
        this(ModEntities.FIRE_EXTINGUISHER_PROJECTILE.get(), level);
        setOwner(owner);
        entityData.set(AMMO, type.legacyMetadata());
        setPos(origin);
        double inaccuracy = 0.0075D * spread;
        Vec3 velocity = new Vec3(
                heading.x + random.nextGaussian() * inaccuracy,
                heading.y + random.nextGaussian() * inaccuracy,
                heading.z + random.nextGaussian() * inaccuracy).normalize().scale(SPEED);
        setDeltaMovement(velocity);
        updateRotation();
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AMMO, FireExtinguisherAmmoType.WATER.legacyMetadata());
    }

    public FireExtinguisherAmmoType ammoType() {
        return FireExtinguisherAmmoType.fromLegacyMetadata(entityData.get(AMMO));
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > LIFE_TICKS) {
            discard();
            return;
        }

        Vec3 velocity = getDeltaMovement();
        Vec3 start = position();
        Vec3 end = start.add(velocity);
        if (level().isClientSide) {
            BlockState particleState = switch (ammoType()) {
                case WATER -> net.minecraft.world.level.block.Blocks.WATER.defaultBlockState();
                case FOAM -> ModBlocks.BLOCK_FOAM.get().defaultBlockState();
                case SAND -> ModBlocks.SAND_MIX.get().defaultBlockState();
            };
            double scatter = ammoType() == FireExtinguisherAmmoType.WATER ? 0.05D : 0.1D;
            level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, particleState),
                    getX(), getY(), getZ(),
                    velocity.x + random.nextGaussian() * scatter,
                    velocity.y - 0.2D + random.nextGaussian() * scatter,
                    velocity.z + random.nextGaussian() * scatter);
            move(end, velocity);
            return;
        }

        BlockHitResult blockHit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 collisionEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        AABB sweep = getBoundingBox().expandTowards(velocity).inflate(0.3D);
        Entity nearest = null;
        Vec3 nearestHit = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity candidate : level().getEntities(this, sweep, this::isImpactCandidate)) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.2D).clip(start, collisionEnd);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestHit = hit.get();
                nearestDistance = distance;
            }
        }

        if (nearest != null) impactEntity(nearestHit, nearest);
        else if (blockHit.getType() != HitResult.Type.MISS) impactBlock(blockHit);
        if (isAlive()) move(end, velocity);
    }

    private void move(Vec3 end, Vec3 velocity) {
        setPos(end);
        setDeltaMovement(velocity.add(0.0D, -GRAVITY, 0.0D));
        updateRotation();
    }

    private boolean isImpactCandidate(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !entity.isPickable()) return false;
        return entity != getOwner() || tickCount >= 20;
    }

    private void impactEntity(Vec3 hit, Entity target) {
        extinguish(target);
        setPos(hit);
        discard();
    }

    static void extinguish(Entity target) {
        if (target.isOnFire()) target.clearFire();
    }

    private void impactBlock(BlockHitResult hit) {
        if (!(level() instanceof ServerLevel level)) return;
        boolean fizz = switch (ammoType()) {
            case WATER -> waterImpact(level, hit.getBlockPos());
            case FOAM -> foamImpact(level, hit);
            case SAND -> sandImpact(level, hit);
        };
        if (fizz) {
            level.playSound(null, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                    1.0F, 1.5F + random.nextFloat() * 0.5F);
        }
        setPos(hit.getLocation());
        discard();
    }

    static boolean waterImpact(ServerLevel level, BlockPos impact) {
        boolean fizz = false;
        for (BlockPos position : BlockPos.betweenClosed(impact.offset(-1, -1, -1), impact.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(position);
            if (state.getBlock() instanceof BaseFireBlock
                    || state.is(ModBlocks.FOAM_LAYER.get()) || state.is(ModBlocks.BLOCK_FOAM.get())) {
                level.removeBlock(position, false);
                fizz = true;
            }
        }
        tryExtinguish(level, impact, FluidStorageTankBlockEntity.ExtinguishType.WATER);
        return fizz;
    }

    private boolean foamImpact(ServerLevel level, BlockHitResult hit) {
        boolean fizz = clearFire(level, hit.getBlockPos());
        if (tryExtinguish(level, hit.getBlockPos(), FluidStorageTankBlockEntity.ExtinguishType.FOAM)) {
            return false;
        }
        BlockPos position = random.nextBoolean()
                ? hit.getBlockPos().relative(hit.getDirection()) : hit.getBlockPos();
        layer(level, position, ModBlocks.FOAM_LAYER.get().defaultBlockState(),
                ModBlocks.BLOCK_FOAM.get().defaultBlockState());
        return fizz;
    }

    private boolean sandImpact(ServerLevel level, BlockHitResult hit) {
        if (tryExtinguish(level, hit.getBlockPos(), FluidStorageTankBlockEntity.ExtinguishType.SAND)) {
            return false;
        }
        BlockPos position = random.nextBoolean()
                ? hit.getBlockPos().relative(hit.getDirection()) : hit.getBlockPos();
        boolean fire = level.getBlockState(position).getBlock() instanceof BaseFireBlock;
        layer(level, position, ModBlocks.SAND_BORON_LAYER.get().defaultBlockState(),
                ModBlocks.SAND_MIX.get().defaultBlockState());
        return fire;
    }

    private static boolean clearFire(ServerLevel level, BlockPos impact) {
        boolean fizz = false;
        for (BlockPos position : BlockPos.betweenClosed(impact.offset(-1, -1, -1), impact.offset(1, 1, 1))) {
            if (level.getBlockState(position).getBlock() instanceof BaseFireBlock) {
                level.removeBlock(position, false);
                fizz = true;
            }
        }
        return fizz;
    }

    static void layer(ServerLevel level, BlockPos position,
                      BlockState layerState, BlockState fullState) {
        BlockState current = level.getBlockState(position);
        if (current.is(layerState.getBlock())) {
            int layers = current.getValue(SnowLayerBlock.LAYERS);
            level.setBlock(position, layers < 7
                    ? current.setValue(SnowLayerBlock.LAYERS, layers + 1) : fullState, 3);
            return;
        }
        if (!current.canBeReplaced()) return;
        if (layerState.canSurvive(level, position)) level.setBlock(position, layerState, 3);
    }

    private static boolean tryExtinguish(ServerLevel level, BlockPos position,
                                         FluidStorageTankBlockEntity.ExtinguishType type) {
        if (level.getBlockEntity(position) instanceof FluidStorageTankBlockEntity tank) {
            tank.tryExtinguish(type);
            return true;
        }
        if (level.getBlockEntity(position) instanceof FluidStorageTankProxyBlockEntity proxy
                && proxy.target() != null) {
            proxy.target().tryExtinguish(type);
            return true;
        }
        BlockState state = level.getBlockState(position);
        if (state.getBlock() instanceof FluidStorageTankBlock) {
            BlockPos core = FluidStorageTankBlock.corePosition(position, state);
            if (level.getBlockEntity(core) instanceof FluidStorageTankBlockEntity tank) {
                tank.tryExtinguish(type);
                return true;
            }
        }
        return false;
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { entityData.set(AMMO, tag.getInt("Ammo")); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("Ammo", entityData.get(AMMO)); }
}
