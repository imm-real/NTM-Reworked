package com.hbm.ntm.entity;

import com.hbm.ntm.explosion.MineExplosion;
import com.hbm.ntm.item.FlamerGunItem;
import com.hbm.ntm.network.ChargeBlastPayload;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModParticles;
import com.hbm.ntm.weapon.FlamerFuelType;
import com.hbm.ntm.weapon.WeaponStatusEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/** Invisible courier for flamethrower particles and impact damage. */
public final class FlameProjectileEntity extends Projectile {
    private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(
            FlameProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(
            FlameProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(
            FlameProjectileEntity.class, EntityDataSerializers.FLOAT);

    public FlameProjectileEntity(EntityType<? extends FlameProjectileEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public FlameProjectileEntity(ServerLevel level, LivingEntity owner, FlamerFuelType fuel,
                                 FlamerGunItem.Variant variant, float damage, float spread,
                                 Vec3 origin, Vec3 heading) {
        this(ModEntities.FLAME_PROJECTILE.get(), level);
        setOwner(owner);
        entityData.set(FUEL, fuel.legacyMetadata());
        entityData.set(VARIANT, variant.ordinal());
        entityData.set(DAMAGE, damage);
        setPos(origin);
        double inaccuracy = 0.0075D * spread;
        Vec3 velocity = new Vec3(
                heading.x + random.nextGaussian() * inaccuracy,
                heading.y + random.nextGaussian() * inaccuracy,
                heading.z + random.nextGaussian() * inaccuracy).normalize().scale(speed(variant));
        setDeltaMovement(velocity);
        updateRotation();
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FUEL, FlamerFuelType.DIESEL.legacyMetadata());
        builder.define(VARIANT, FlamerGunItem.Variant.FLAMETHROWER.ordinal());
        builder.define(DAMAGE, 0.0F);
    }

    public FlamerFuelType fuel() { return FlamerFuelType.fromLegacyMetadata(entityData.get(FUEL)); }

    public FlamerGunItem.Variant variant() {
        int ordinal = entityData.get(VARIANT);
        return ordinal >= 0 && ordinal < FlamerGunItem.Variant.values().length
                ? FlamerGunItem.Variant.values()[ordinal] : FlamerGunItem.Variant.FLAMETHROWER;
    }

    public float damage() { return entityData.get(DAMAGE); }

    @Override
    public void tick() {
        super.tick();
        FlamerFuelType fuel = fuel();
        FlamerGunItem.Variant variant = variant();
        if (tickCount > life(fuel, variant)) {
            discard();
            return;
        }

        Vec3 velocity = getDeltaMovement();
        Vec3 start = position();
        Vec3 end = start.add(velocity);
        if (level().isClientSide) {
            level().addParticle(fuel.isBalefire()
                            ? ModParticles.FLAMETHROWER_BALEFIRE.get()
                            : ModParticles.FLAMETHROWER_FIRE.get(),
                    getX(), getY() - 0.125D, getZ(), 0.0D, 0.0D, 0.0D);
            setPos(end);
            setDeltaMovement(velocity.add(0.0D, -gravity(fuel, variant), 0.0D));
            updateRotation();
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

        if (isAlive()) {
            setPos(end);
            setDeltaMovement(velocity.add(0.0D, -gravity(fuel, variant), 0.0D));
            updateRotation();
        }
    }

    private boolean isImpactCandidate(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !entity.isPickable()) return false;
        return entity != getOwner() || tickCount >= 20;
    }

    private void impactEntity(Vec3 hit, Entity target) {
        if (!(level() instanceof ServerLevel level)) return;
        if (variant() == FlamerGunItem.Variant.DAYBREAKER) {
            daybreakerImpact(level, hit);
            return;
        }
        if (target instanceof LivingEntity living) {
            if (fuel().isBalefire()) WeaponStatusEvents.applyBalefire(living, 200);
            else WeaponStatusEvents.applyFire(living, 100);
            float amount = damage();
            double head = living.getBbHeight() - living.getEyeHeight();
            if (hit.y > living.getY() + living.getBbHeight() - head * 2.0D) amount *= 1.25F;
            Vec3 motion = living.getDeltaMovement();
            living.invulnerableTime = 0;
            living.hurt(level.damageSources().source(ModDamageTypes.FLAMETHROWER, this, getOwner()), amount);
            living.setDeltaMovement(motion);
        } else {
            target.hurt(level.damageSources().source(ModDamageTypes.FLAMETHROWER, this, getOwner()), damage());
        }
        setPos(hit);
        discard();
    }

    private void impactBlock(BlockHitResult hit) {
        if (!(level() instanceof ServerLevel level)) return;
        if (variant() == FlamerGunItem.Variant.DAYBREAKER) {
            daybreakerImpact(level, hit.getLocation());
            return;
        }

        boolean ignited = igniteIfPossible(level, hit);
        FlamerFuelType fuel = fuel();
        if (fuel.isBalefire()) {
            spawnLingering(level, hit.getLocation(), fuel.lingerWidth(), fuel.lingerHeight(),
                    fuel.lingerTicks(), LingeringFireEntity.Kind.BALEFIRE);
        } else if (fuel.lingers() && !ignited) {
            spawnLingering(level, hit.getLocation(), fuel.lingerWidth(), fuel.lingerHeight(),
                    fuel.lingerTicks(), LingeringFireEntity.Kind.FIRE);
        }
        setPos(hit.getLocation());
        discard();
    }

    private void daybreakerImpact(ServerLevel level, Vec3 hit) {
        FlamerFuelType fuel = fuel();
        float radius = fuel == FlamerFuelType.NAPALM ? 7.5F : 5.0F;
        MineExplosion.blastEntities(level, hit.x, hit.y, hit.z,
                radius, damage(), 1.0D, 0.0F, 0.0F, 1.0F, this, getOwner());
        PacketDistributor.sendToPlayersNear(level, null, hit.x, hit.y, hit.z, 200.0D,
                new ChargeBlastPayload(hit.x, hit.y, hit.z, false));

        if (fuel != FlamerFuelType.GAS) {
            double width = fuel == FlamerFuelType.BALEFIRE ? 7.5D : 6.0D;
            double height = fuel == FlamerFuelType.BALEFIRE ? 2.5D : 2.0D;
            int duration = switch (fuel) {
                case DIESEL -> 200;
                case NAPALM -> 300;
                case BALEFIRE -> 400;
                case GAS -> 0;
            };
            spawnLingering(level, hit, width, height, duration,
                    fuel.isBalefire() ? LingeringFireEntity.Kind.BALEFIRE : LingeringFireEntity.Kind.FIRE);
        }
        setPos(hit);
        discard();
    }

    private static boolean igniteIfPossible(ServerLevel level, BlockHitResult hit) {
        BlockPos blockPos = hit.getBlockPos();
        BlockState state = level.getBlockState(blockPos);
        if (!state.isFlammable(level, blockPos, hit.getDirection().getOpposite())) return false;
        BlockPos firePos = blockPos.relative(hit.getDirection());
        if (!level.getBlockState(firePos).isAir()) return false;
        level.setBlock(firePos, BaseFireBlock.getState(level, firePos), 11);
        return true;
    }

    private void spawnLingering(ServerLevel level, Vec3 hit, double width, double height,
                                int duration, LingeringFireEntity.Kind kind) {
        if (duration <= 0) return;
        AABB nearby = new AABB(hit, hit).inflate(width * 0.5D + 0.5D,
                height * 0.5D + 0.5D, width * 0.5D + 0.5D);
        if (!level.getEntitiesOfClass(LingeringFireEntity.class, nearby).isEmpty()) return;
        level.addFreshEntity(new LingeringFireEntity(ModEntities.LINGERING_FIRE.get(), level,
                hit.x, hit.y, hit.z, duration, width, height, kind));
    }

    public static int life(FlamerFuelType fuel, FlamerGunItem.Variant variant) {
        if (variant == FlamerGunItem.Variant.DAYBREAKER) return 200;
        if (variant == FlamerGunItem.Variant.TOPAZ && fuel != FlamerFuelType.GAS) return 60;
        return fuel.lifeTicks();
    }

    public static float gravity(FlamerFuelType fuel, FlamerGunItem.Variant variant) {
        if (variant == FlamerGunItem.Variant.DAYBREAKER) return 0.035F;
        if (variant == FlamerGunItem.Variant.TOPAZ) return 0.0F;
        return fuel.gravity();
    }

    public static float speed(FlamerGunItem.Variant variant) {
        return variant == FlamerGunItem.Variant.DAYBREAKER ? 2.0F : 1.0F;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(FUEL, tag.getInt("Fuel"));
        entityData.set(VARIANT, tag.getInt("Variant"));
        entityData.set(DAMAGE, tag.getFloat("Damage"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Fuel", entityData.get(FUEL));
        tag.putInt("Variant", entityData.get(VARIANT));
        tag.putFloat("Damage", entityData.get(DAMAGE));
    }
}
