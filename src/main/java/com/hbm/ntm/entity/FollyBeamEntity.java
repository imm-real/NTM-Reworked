package com.hbm.ntm.entity;

import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModEntities;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public final class FollyBeamEntity extends Projectile {
    public static final double RANGE = 250.0D;
    public static final int LIFETIME = 100;

    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(FollyBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DIRECTION =
            SynchedEntityData.defineId(FollyBeamEntity.class, EntityDataSerializers.VECTOR3);

    public FollyBeamEntity(EntityType<? extends FollyBeamEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        noCulling = true;
    }

    public FollyBeamEntity(ServerLevel level, LivingEntity shooter, float damage, Vec3 origin, Vec3 heading) {
        this(ModEntities.FOLLY_BEAM.get(), level);
        setOwner(shooter);
        entityData.set(DAMAGE, damage);
        Vec3 direction = heading.normalize();
        entityData.set(DIRECTION, new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
        setYRot((float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG));
        setXRot((float) (-Math.asin(direction.y) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
        setPos(origin);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 0.0F);
        builder.define(DIRECTION, new Vector3f(0.0F, 0.0F, 1.0F));
    }

    public float beamDamage() { return entityData.get(DAMAGE); }
    public float beamLength() { return (float) RANGE; }

    public Vec3 beamDirection() {
        Vector3f direction = entityData.get(DIRECTION);
        Vec3 beam = new Vec3(direction.x, direction.y, direction.z);
        return beam.lengthSqr() > 1.0E-8D ? beam.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount == 2) erase((ServerLevel) level());
        if (tickCount > LIFETIME) discard();
    }

    private void erase(ServerLevel level) {
        if (getOwner() instanceof LivingEntity living) RadiationSystem.contaminate(living, 150.0F, false);
        Vec3 direction = beamDirection();
        Vec3 end = position().add(direction.scale(RANGE));
        List<Entity> targets = level.getEntities(this, new AABB(position(), end).inflate(1.0D),
                entity -> entity != getOwner() && entity.isAlive() && !entity.isSpectator());
        for (int distance = 1; distance < RANGE; distance += 2) {
            BlockPos center = BlockPos.containing(position().add(direction.scale(distance)));
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                BlockPos pos = center.offset(x, y, z);
                if (level.isInWorldBounds(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                AABB damageBox = new AABB(pos).inflate(1.0D);
                for (Entity target : targets) {
                    if (!target.getBoundingBox().intersects(damageBox)) continue;
                    DamageSource source = level.damageSources().source(ModDamageTypes.SUBATOMIC, this, getOwner());
                    float damage = beamDamage();
                    if (target instanceof LivingEntity living) {
                        living.invulnerableTime = 0;
                        damage = compensateForArmor(living, source, damage);
                    }
                    target.hurt(source, damage);
                }
            }
        }
    }

    private static float compensateForArmor(LivingEntity living, DamageSource source, float damage) {
        if (living.getArmorValue() <= 0) return damage;
        float armor = living.getArmorValue();
        float toughness = (float) living.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float effectiveArmor = Math.max(0.0F, armor - 100.0F) * 0.01F;
        float target = CombatRules.getDamageAfterAbsorb(living, damage, source, effectiveArmor, toughness);
        float low = 0.0F;
        float high = Math.max(damage * 4.0F, damage + armor + 1.0F);
        while (CombatRules.getDamageAfterAbsorb(living, high, source, armor, toughness) < target
                && high < 65_536.0F) high *= 2.0F;
        for (int i = 0; i < 24; i++) {
            float middle = (low + high) * 0.5F;
            if (CombatRules.getDamageAfterAbsorb(living, middle, source, armor, toughness) < target) low = middle;
            else high = middle;
        }
        return high;
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
}
