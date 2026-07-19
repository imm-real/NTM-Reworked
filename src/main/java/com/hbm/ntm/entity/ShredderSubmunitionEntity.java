package com.hbm.ntm.entity;

import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.weapon.Shotgun12GaugeAmmoType;
import com.hbm.ntm.weapon.WeaponStatusEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** Tiny plasma problem that bounces three times before accepting death. */
public final class ShredderSubmunitionEntity extends Projectile {
    /** Half-speed, twice the screen time. */
    private static final double MOTION_MULT = 0.5D;
    /** Fifty ticks to find something expensive. */
    private static final int LIFE_TICKS = 50;
    /** Brief owner immunity, for plausible deniability. */
    private static final int SELF_DAMAGE_DELAY = 2;
    /** Small plasma apology around every bounce. */
    private static final double RICOCHET_AOE = 0.5D;

    private static final EntityDataAccessor<Integer> AMMO =
            SynchedEntityData.defineId(ShredderSubmunitionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(ShredderSubmunitionEntity.class, EntityDataSerializers.FLOAT);

    private Vec3 velocity = Vec3.ZERO;
    private int ricochets;

    public ShredderSubmunitionEntity(EntityType<? extends ShredderSubmunitionEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** @param direction somewhere generally in front of the gun */
    public ShredderSubmunitionEntity(ServerLevel level, LivingEntity owner, Shotgun12GaugeAmmoType ammo,
                                     float damage, Vec3 origin, Vec3 direction) {
        this(ModEntities.SHREDDER_SUBMUNITION.get(), level);
        setOwner(owner);
        entityData.set(AMMO, ammo.legacyMetadata());
        entityData.set(DAMAGE, damage);
        setPos(origin);

        // Normalize, sprinkle Gaussian incompetence, launch.
        double length = direction.length();
        Vec3 unit = length <= 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.scale(1.0D / length);
        float inaccuracy = ammo.spread() + 0.2F;
        Vec3 motion = new Vec3(
                unit.x + random.nextGaussian() * inaccuracy,
                unit.y + random.nextGaussian() * inaccuracy,
                unit.z + random.nextGaussian() * inaccuracy);
        this.velocity = motion;
        updateRotation(motion);
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AMMO, Shotgun12GaugeAmmoType.BUCKSHOT.legacyMetadata());
        builder.define(DAMAGE, 0.0F);
    }

    public Shotgun12GaugeAmmoType ammoType() {
        return Shotgun12GaugeAmmoType.fromLegacyMetadata(entityData.get(AMMO));
    }

    public float damage() {
        return entityData.get(DAMAGE);
    }

    public int ricochets() {
        return ricochets;
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > LIFE_TICKS) {
            discard();
            return;
        }

        Vec3 step = velocity.scale(MOTION_MULT);
        if (level().isClientSide) {
            setPos(position().add(step));
            updateRotation(velocity);
            return;
        }

        Vec3 start = position();
        Vec3 end = start.add(step);
        BlockHitResult blockHit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 entityEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        AABB sweep = getBoundingBox().expandTowards(step).inflate(1.0D);
        Entity nearestEntity = null;
        Vec3 nearestHit = null;
        double nearestDist = 0.0D;
        for (Entity candidate : level().getEntities(this, sweep, this::canHit)) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(start, entityEnd);
            if (hit.isEmpty()) continue;
            double dist = start.distanceToSqr(hit.get());
            if (nearestEntity == null || dist < nearestDist) {
                nearestEntity = candidate;
                nearestHit = hit.get();
                nearestDist = dist;
            }
        }

        // Meat wins ties against walls.
        if (nearestEntity != null) {
            handleImpact(nearestEntity, nearestHit, null);
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            handleImpact(null, blockHit.getLocation(), blockHit);
        }

        if (isAlive()) {
            setPos(position().add(velocity.scale(MOTION_MULT)));
            updateRotation(velocity);
        }
    }

    private boolean canHit(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !entity.isPickable()) return false;
        return entity != getOwner() || tickCount >= SELF_DAMAGE_DELAY;
    }

    /** Explode, bounce, hurt. Stop when one of those finally kills it. */
    private void handleImpact(Entity target, Vec3 hitVec, BlockHitResult blockHit) {
        onImpact(target, hitVec);
        if (!isAlive()) return;
        if (blockHit != null) onRicochet(blockHit, hitVec);
        if (!isAlive()) return;
        if (target != null) onEntityHit(target, hitVec);
    }

    /** Explosive loads explode; phosphorus explains itself with fire. */
    private void onImpact(Entity target, Vec3 hitVec) {
        Shotgun12GaugeAmmoType ammo = ammoType();
        if (ammo.impactExplosionRadius() > 0.0F) {
            explodeAt(hitVec);
            return;
        }
        if (ammo.phosphorusTicks() > 0 && target instanceof LivingEntity living) {
            WeaponStatusEvents.applyPhosphorus(living, ammo.phosphorusTicks());
        }
    }

    /** Glass breaks; everything else gets a plasma bounce. */
    private void onRicochet(BlockHitResult blockHit, Vec3 hitVec) {
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = level().getBlockState(pos);
        if (isGlass(state)) {
            level().destroyBlock(pos, false);
            setPos(hitVec);
            return;
        }
        // TODO shootable detonators and CRT abuse

        Direction side = blockHit.getDirection();
        Vec3 face = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 vel = velocity.lengthSqr() <= 1.0E-9D ? face : velocity.normalize();
        // Angle between incoming bad decision and wall.
        double crossAngle = Math.toDegrees(Math.acos(Mth.clamp(vel.dot(face), -1.0D, 1.0D)));
        if (crossAngle >= 180.0D) crossAngle -= 180.0D;
        double grazing = Math.abs(crossAngle - 90.0D);

        // Ninety-degree allowance means every wall says yes.
        if (grazing > 90.0D) {
            setPos(hitVec);
            discard();
            return;
        }

        applyRicochetBlast();
        ricochets++;
        if (ricochets > 3) {
            setPos(hitVec);
            discard();
        }
        velocity = switch (side.getAxis()) {
            case X -> new Vec3(-velocity.x, velocity.y, velocity.z);
            case Y -> new Vec3(velocity.x, -velocity.y, velocity.z);
            case Z -> new Vec3(velocity.x, velocity.y, -velocity.z);
        };
        setPos(hitVec);
        // Silent bounce. The plasma damage is the notification sound.
    }

    /** Half-block plasma hug, armor included. */
    private void applyRicochetBlast() {
        if (!(level() instanceof ServerLevel server)) return;
        Entity owner = getOwner();
        DamageSource source = server.damageSources().source(ModDamageTypes.PLASMA, this, owner);
        AABB box = new AABB(position(), position()).inflate(RICOCHET_AOE);
        for (Entity e : server.getEntities(this, box, Entity::isAlive)) {
            if (e instanceof LivingEntity living) {
                living.invulnerableTime = 0;
                Vec3 keep = living.getDeltaMovement();
                living.hurt(source, damage());
                living.setDeltaMovement(keep);
            } else {
                e.hurt(source, damage());
            }
        }
    }

    /** Standard entity hit, plasma-flavored. */
    private void onEntityHit(Entity target, Vec3 hitVec) {
        if (target == getOwner() && tickCount < SELF_DAMAGE_DELAY) return;
        Shotgun12GaugeAmmoType ammo = ammoType();
        Entity owner = getOwner();
        DamageSource source = level().damageSources().source(ModDamageTypes.PLASMA, this, owner);
        if (!(target instanceof LivingEntity living)) {
            target.hurt(source, damage());
            setPos(hitVec);
            discard();
            return;
        }
        if (!living.isAlive()) return;

        float intended = damage();
        if (ammo.headshotMultiplier() > 1.0F) {
            double head = living.getBbHeight() - living.getEyeHeight();
            if (hitVec.y > living.getY() + living.getBbHeight() - head * 2.0D) {
                intended *= ammo.headshotMultiplier();
            }
        }
        float applied = compensateForArmorPiercing(living, source, intended,
                ammo.armorThresholdNegation(), ammo.armorPiercing());

        float healthBefore = living.getHealth();
        Vec3 keep = living.getDeltaMovement();
        living.invulnerableTime = 0;
        boolean hurt = living.hurt(source, applied);
        living.setDeltaMovement(keep);
        if (hurt && owner != null) {
            double dx = living.getX() - owner.getX();
            double dz = living.getZ() - owner.getZ();
            if (dx * dx + dz * dz < 1.0E-4D) {
                dx = (random.nextDouble() - random.nextDouble()) * 0.01D;
                dz = (random.nextDouble() - random.nextDouble()) * 0.01D;
            }
            living.knockback(0.1D, -dx, -dz);
        }
        // No penetration means this is the last damage calculation.
        entityData.set(DAMAGE, damage() - Math.max(healthBefore - living.getHealth(), 0.0F) * 0.5F);
        setPos(hitVec);
        discard();
    }

    /** Range-two entity blender for explosive shells. */
    private void explodeAt(Vec3 center) {
        if (!(level() instanceof ServerLevel level)) {
            discard();
            return;
        }
        Shotgun12GaugeAmmoType ammo = ammoType();
        double range = ammo.impactExplosionRadius() * 2.0D;
        Entity owner = getOwner();
        DamageSource source = level.damageSources().explosion(this, owner);
        AABB area = new AABB(center, center).inflate(range + 1.0D);
        for (Entity target : level.getEntities(this, area, Entity::isAlive)) {
            AABB bounds = target.getBoundingBox();
            double closestX = Mth.clamp(center.x, bounds.minX, bounds.maxX);
            double closestY = Mth.clamp(center.y, bounds.minY, bounds.maxY);
            double closestZ = Mth.clamp(center.z, bounds.minZ, bounds.maxZ);
            double distance = center.distanceTo(new Vec3(closestX, closestY, closestZ));
            double scaled = distance / range;
            float density = exposure(center, target);
            if (scaled > 1.0D || density < 0.125F) continue;

            float amount = (float) (damage() * (1.0D - scaled));
            if (target == owner) amount *= 0.5F;
            if (amount <= 0.0F) continue;
            if (target instanceof LivingEntity living) {
                amount = compensateForArmorPiercing(living, source, amount,
                        ammo.armorThresholdNegation(), ammo.armorPiercing());
                living.invulnerableTime = 0;
            }
            if (target.hurt(source, amount)) {
                Vec3 push = target.getEyePosition().subtract(center);
                if (push.lengthSqr() > 1.0E-6D) {
                    target.setDeltaMovement(target.getDeltaMovement().add(
                            push.normalize().scale((1.0D - scaled) * density)));
                }
            }
        }
        setPos(center);
        discard();
    }

    private static float exposure(Vec3 center, Entity target) {
        Vec3[] nodes = {
                center,
                center.add(1.0D, 0.0D, 0.0D), center.add(-1.0D, 0.0D, 0.0D),
                center.add(0.0D, 1.0D, 0.0D), center.add(0.0D, -1.0D, 0.0D),
                center.add(0.0D, 0.0D, 1.0D), center.add(0.0D, 0.0D, -1.0D)
        };
        float density = 0.0F;
        for (Vec3 node : nodes) density = Math.max(density, Explosion.getSeenPercent(node, target));
        return density;
    }

    private static float compensateForArmorPiercing(LivingEntity living, DamageSource source,
                                                    float intendedDamage, float thresholdNegation,
                                                    float armorPiercing) {
        if ((armorPiercing == 0.0F && thresholdNegation == 0.0F) || living.getArmorValue() <= 0) {
            return intendedDamage;
        }
        float armor = living.getArmorValue();
        float toughness = (float) living.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float effectiveArmor = Math.max(0.0F, armor - thresholdNegation)
                * Mth.clamp(1.0F - armorPiercing, 0.0F, 2.0F);
        float targetAfterArmor = CombatRules.getDamageAfterAbsorb(
                living, intendedDamage, source, effectiveArmor, toughness);
        float low = 0.0F;
        float high = Math.max(intendedDamage * 4.0F, intendedDamage + armor + 1.0F);
        while (CombatRules.getDamageAfterAbsorb(living, high, source, armor, toughness) < targetAfterArmor
                && high < 4096.0F) {
            high *= 2.0F;
        }
        for (int i = 0; i < 24; i++) {
            float mid = (low + high) * 0.5F;
            float result = CombatRules.getDamageAfterAbsorb(living, mid, source, armor, toughness);
            if (result < targetAfterArmor) low = mid;
            else high = mid;
        }
        return (low + high) * 0.5F;
    }

    private static boolean isGlass(BlockState state) {
        return state.getSoundType() == SoundType.GLASS;
    }

    private void updateRotation(Vec3 motion) {
        if (motion.lengthSqr() <= 0.0D) return;
        setYRot((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z))
                * Mth.RAD_TO_DEG));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("ammo", entityData.get(AMMO));
        tag.putFloat("damage", entityData.get(DAMAGE));
        tag.putInt("ricochets", ricochets);
        tag.putDouble("velX", velocity.x);
        tag.putDouble("velY", velocity.y);
        tag.putDouble("velZ", velocity.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(AMMO, tag.getInt("ammo"));
        entityData.set(DAMAGE, tag.getFloat("damage"));
        ricochets = tag.getInt("ricochets");
        velocity = new Vec3(tag.getDouble("velX"), tag.getDouble("velY"), tag.getDouble("velZ"));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
