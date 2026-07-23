package com.hbm.ntm.entity;

import com.hbm.ntm.item.MaskFilterStorage;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.SevenSixTwoAmmoType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class MaskManEntity extends Monster implements RadiationSystem.RadiationImmune {
    public static final float MAX_HEALTH = 1_000.0F;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private LaserAttack laserAttack;
    private int laserTimer;
    private int laserCount;
    private int minigunTimer = 3;
    private float lastHealth = MAX_HEALTH;

    public MaskManEntity(EntityType<? extends MaskManEntity> type, Level level) {
        super(type, level);
        xpReward = 100;
        laserAttack = LaserAttack.values()[random.nextInt(LaserAttack.values().length)];
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 100.0D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MAX_HEALTH, MAX_HEALTH);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new CasualApproachGoal());
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;

        double distance = distanceTo(target);
        if (distance > 5.0D && distance < 10.0D) tickMinigun(target);
        if (distance > 10.0D) tickLaserAttacks(target);
    }

    private void tickMinigun(LivingEntity target) {
        getLookControl().setLookAt(target, 15.0F, 15.0F);
        if (--minigunTimer > 0 || !(level() instanceof ServerLevel level)) return;
        minigunTimer = 3;

        Vec3 muzzleOffset = new Vec3(-1.5D, -1.5D, 0.0D)
                .yRot(-yBodyRot * Mth.DEG_TO_RAD);
        Vec3 origin = position().add(0.0D, getEyeHeight(), 0.0D).add(muzzleOffset);
        Vec3 heading = target.position().add(0.0D, target.getBbHeight() / 3.0D, 0.0D)
                .subtract(origin).normalize();
        level.addFreshEntity(new BulletEntity(level, this, SevenSixTwoAmmoType.FULL_METAL_JACKET,
                4.0F, 0.075F, origin, heading));
        level.playSound(null, blockPosition(), ModSounds.GUN_MINIGUN_FIRE.get(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private void tickLaserAttacks(LivingEntity target) {
        if (--laserTimer > 0 || !(level() instanceof ServerLevel level)) return;
        laserTimer = laserAttack.delay;

        switch (laserAttack) {
            case ORB -> {
                MaskManProjectileEntity orb = MaskManProjectileEntity.aimed(
                        level, this, target, MaskManProjectileEntity.Kind.ORB, 2.0F, 0.0F);
                orb.setDeltaMovement(orb.getDeltaMovement().add(0.0D, 0.5D, 0.0D));
                level.addFreshEntity(orb);
                level.playSound(null, blockPosition(), ModSounds.GUN_TESLA_FIRE.get(),
                        SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            case MISSILE -> {
                MaskManProjectileEntity missile = MaskManProjectileEntity.aimed(
                        level, this, target, MaskManProjectileEntity.Kind.ROCKET, 1.0F, 0.0F);
                Vec3 horizontal = new Vec3(target.getX() - getX(), 0.0D, target.getZ() - getZ());
                missile.setDeltaMovement(horizontal.x * 0.05D,
                        0.5D + random.nextDouble() * 0.5D, horizontal.z * 0.05D);
                level.addFreshEntity(missile);
                level.playSound(null, blockPosition(), ModSounds.GUN_UNDERBARREL_FIRE.get(),
                        SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            case SPLASH -> {
                for (int index = 0; index < 5; index++) {
                    level.addFreshEntity(MaskManProjectileEntity.aimed(
                            level, this, target, MaskManProjectileEntity.Kind.TRACER, 1.0F, 0.05F));
                }
            }
        }

        if (++laserCount >= laserAttack.amount) {
            laserCount = 0;
            int next = laserAttack.ordinal() + random.nextInt(LaserAttack.values().length - 1);
            laserAttack = LaserAttack.values()[next % LaserAttack.values().length];
        }
        setYRot(yHeadRot);
        yBodyRot = yHeadRot;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof ThrownEgg && random.nextInt(10) == 0) {
            xpReward = 0;
            setHealth(0.0F);
            die(source);
            return true;
        }
        if (source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)) amount = 0.0F;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) amount *= 0.5F;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) amount *= 0.5F;
        if (amount > 50.0F) amount = 50.0F + (amount - 50.0F) * 0.5F;
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (lastHealth >= getMaxHealth() * 0.5F
                    && getHealth() < getMaxHealth() * 0.5F && isAlive()) {
                level().explode(this, getX(), getY() + 4.0D, getZ(), 2.5F, true,
                        Level.ExplosionInteraction.MOB);
            }
            lastHealth = getHealth();
            bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
            bossEvent.setName(getDisplayName());
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        ItemStack mask = new ItemStack(ModItems.GAS_MASK_M65.get());
        MaskFilterStorage.install(mask, new ItemStack(ModItems.GAS_MASK_FILTER_COMBO.get()));
        spawnAtLocation(mask);
        spawnAtLocation(ModItems.COIN_MASKMAN.get());
        spawnAtLocation(Items.SKELETON_SKULL);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LaserAttack", laserAttack.ordinal());
        tag.putInt("LaserTimer", laserTimer);
        tag.putInt("LaserCount", laserCount);
        tag.putInt("MinigunTimer", minigunTimer);
        tag.putFloat("LastHealth", lastHealth);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        laserAttack = LaserAttack.values()[Math.floorMod(tag.getInt("LaserAttack"), LaserAttack.values().length)];
        laserTimer = tag.getInt("LaserTimer");
        laserCount = tag.getInt("LaserCount");
        minigunTimer = tag.getInt("MinigunTimer");
        lastHealth = tag.contains("LastHealth") ? tag.getFloat("LastHealth") : getHealth();
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    private enum LaserAttack {
        ORB(60, 5),
        MISSILE(10, 10),
        SPLASH(40, 3);

        private final int delay;
        private final int amount;

        LaserAttack(int delay, int amount) {
            this.delay = delay;
            this.amount = amount;
        }
    }

    private final class CasualApproachGoal extends Goal {
        private int pathTimer;
        private Vec3 destination;

        private CasualApproachGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive()) return false;
            if (--pathTimer > 0) return true;
            destination = approachPosition(target);
            pathTimer = 4 + random.nextInt(7);
            return getNavigation().createPath(destination.x, destination.y, destination.z, 0) != null;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && !getNavigation().isDone();
        }

        @Override
        public void start() {
            if (destination != null) {
                getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0D);
            }
            pathTimer = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--pathTimer > 0) return;
            destination = approachPosition(target);
            pathTimer = 4 + random.nextInt(7);
            if (distanceToSqr(target) > 1_024.0D) pathTimer += 10;
            else if (distanceToSqr(target) > 256.0D) pathTimer += 5;
            if (!getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0D)) {
                pathTimer += 15;
            }
        }

        private Vec3 approachPosition(LivingEntity target) {
            Vec3 offset = position().subtract(target.position());
            double range = Math.min(offset.length(), 20.0D) - 10.0D;
            Vec3 direction = offset.lengthSqr() < 1.0E-8D ? Vec3.ZERO : offset.normalize();
            return new Vec3(
                    getX() + direction.x * range + random.nextGaussian() * 2.0D,
                    getY() + direction.y - 5.0D + random.nextInt(11),
                    getZ() + direction.z * range + random.nextGaussian() * 2.0D);
        }
    }
}
