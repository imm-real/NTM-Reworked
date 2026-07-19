package com.hbm.ntm.entity;

import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.hazard.HazardSystem;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative chlorine cloud. Hazardous gas, not festive decoration. */
public final class ChlorineCloudEntity extends Entity {
    public static final int MIN_LIFETIME = 700;
    public static final int MAX_LIFETIME = 800;
    private static final int POISON_ROLL = 50;
    private static final double POISON_RANGE = 2.0D;
    private static final double DAMPING = 0.7599999785423279D;
    private static final int MOVEMENT_SUBDIVISIONS = 4;

    private int particleAge;
    private int maxAge;

    public ChlorineCloudEntity(EntityType<? extends ChlorineCloudEntity> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    public static ChlorineCloudEntity create(ServerLevel level, double x, double y, double z,
                                               double xVelocity, double yVelocity, double zVelocity) {
        ChlorineCloudEntity cloud = new ChlorineCloudEntity(ModEntities.CHLORINE_CLOUD.get(), level);
        cloud.setPos(x, y, z);

        // EntityModFX first randomised a zero input velocity, normalised it, and only
        // then EntityChlorineFX damped that result by 0.1 before adding the vent vector.
        double xd = (Math.random() * 2.0D - 1.0D) * 0.4F;
        double yd = (Math.random() * 2.0D - 1.0D) * 0.4F;
        double zd = (Math.random() * 2.0D - 1.0D) * 0.4F;
        float speed = (float) (Math.random() + Math.random() + 1.0D) * 0.15F;
        float length = Mth.sqrt((float) (xd * xd + yd * yd + zd * zd));
        xd = xd / length * speed * 0.4000000059604645D;
        yd = yd / length * speed * 0.4000000059604645D + 0.10000000149011612D;
        zd = zd / length * speed * 0.4000000059604645D;
        cloud.setDeltaMovement(
                xd * 0.10000000149011612D + xVelocity,
                yd * 0.10000000149011612D + yVelocity,
                zd * 0.10000000149011612D + zVelocity);
        return cloud;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        xo = getX();
        yo = getY();
        zo = getZ();

        if (maxAge < MIN_LIFETIME) maxAge = random.nextInt(101) + MIN_LIFETIME;
        if (!level().isClientSide && random.nextInt(POISON_ROLL) == 0) poisonNearby();

        particleAge++;
        if (particleAge >= maxAge) discard();

        Vec3 movement = getDeltaMovement().scale(DAMPING);
        if (onGround()) movement = movement.multiply(0.699999988079071D, 1.0D, 0.699999988079071D);
        BlockPos skyPosition = new BlockPos(Mth.floor(getX()), Mth.floor(getY()), Mth.floor(getZ()));
        if (level().isRaining() && level().canSeeSky(skyPosition)) movement = movement.add(0.0D, -0.01D, 0.0D);
        setDeltaMovement(movement);

        for (int i = 0; i < MOVEMENT_SUBDIVISIONS; i++) {
            Vec3 step = getDeltaMovement().scale(1.0D / MOVEMENT_SUBDIVISIONS);
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
            BlockPos checked = new BlockPos((int) getX(), (int) getY(), (int) getZ());
            BlockState state = level().getBlockState(checked);
            boolean normalCube = state.canOcclude() && state.isCollisionShapeFullBlock(level(), checked);
            if (normalCube) {
                if (random.nextInt(5) != 0) discard();
                setPos(getX() - step.x, getY() - step.y, getZ() - step.z);
                setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    private void poisonNearby() {
        double x = (int) getX();
        double y = (int) getY();
        double z = (int) getZ();
        AABB bounds = new AABB(x - POISON_RANGE, y - POISON_RANGE, z - POISON_RANGE,
                x + POISON_RANGE, y + POISON_RANGE, z + POISON_RANGE);
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, bounds)) {
            if (living.distanceToSqr(x, y, z) > POISON_RANGE * POISON_RANGE) continue;
            if (HazardSystem.hasProtection(living, HazardProtection.GAS_LUNG, 1)
                    || HazardSystem.hasProtection(living, HazardProtection.GAS_BLISTERING, 1)) continue;
            living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 5 * 20, 0));
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 20, 2));
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, 20, 1));
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30 * 20, 1));
            living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 30 * 20, 2));
        }
    }

    public int particleAge() {
        return particleAge;
    }

    public int maxAge() {
        return maxAge;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // EntityModFX persisted only age; maxAge intentionally rerolls after a reload.
        tag.putShort("age", (short) particleAge);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        particleAge = tag.getShort("age");
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
