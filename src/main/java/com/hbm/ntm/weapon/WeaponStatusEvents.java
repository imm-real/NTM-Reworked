package com.hbm.ntm.weapon;

import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Shared persistent status effects applied by Sedna ammunition. */
public final class WeaponStatusEvents {
    private static final String PHOSPHORUS = "hbm_phosphorus";
    private static final String FIRE = "hbm_flamer_fire";
    private static final String BALEFIRE = "hbm_flamer_balefire";

    private WeaponStatusEvents() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WeaponStatusEvents::onEntityTick);
    }

    public static void applyPhosphorus(LivingEntity entity, int ticks) {
        if (ticks <= 0 || entity.fireImmune()) return;
        entity.getPersistentData().putInt(PHOSPHORUS,
                Math.max(entity.getPersistentData().getInt(PHOSPHORUS), ticks));
    }

    public static int phosphorusTicks(LivingEntity entity) {
        return Math.max(0, entity.getPersistentData().getInt(PHOSPHORUS));
    }

    public static void applyFire(LivingEntity entity, int ticks) {
        if (ticks <= 0 || entity.fireImmune()) return;
        entity.getPersistentData().putInt(FIRE,
                Math.max(entity.getPersistentData().getInt(FIRE), ticks));
    }

    public static void applyBalefire(LivingEntity entity, int ticks) {
        if (ticks <= 0) return;
        entity.getPersistentData().putInt(BALEFIRE,
                Math.max(entity.getPersistentData().getInt(BALEFIRE), ticks));
    }

    public static int fireTicks(LivingEntity entity) {
        return Math.max(0, entity.getPersistentData().getInt(FIRE));
    }

    public static int balefireTicks(LivingEntity entity) {
        return Math.max(0, entity.getPersistentData().getInt(BALEFIRE));
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || !(living.level() instanceof ServerLevel level)) return;
        tickPhosphorus(living, level);
        tickFire(living, level);
        tickBalefire(living, level);
    }

    private static void tickPhosphorus(LivingEntity living, ServerLevel level) {
        int ticks = phosphorusTicks(living);
        if (ticks <= 0) return;
        if (!living.isAlive() || living.fireImmune()) {
            living.getPersistentData().remove(PHOSPHORUS);
            return;
        }
        living.getPersistentData().putInt(PHOSPHORUS, ticks - 1);
        fizzle(living, level);
        damageWithoutKnockback(living, 40, 5.0F);
        level.sendParticles(ParticleTypes.FLAME, particleX(living), particleY(living), particleZ(living),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void tickFire(LivingEntity living, ServerLevel level) {
        int ticks = fireTicks(living);
        if (ticks <= 0) return;
        if (!living.isAlive() || living.fireImmune() || living.isInWaterRainOrBubble()) {
            living.getPersistentData().remove(FIRE);
            return;
        }
        living.getPersistentData().putInt(FIRE, ticks - 1);
        fizzle(living, level);
        damageWithoutKnockback(living, 40, 2.0F);
        level.sendParticles(ModParticles.FLAMETHROWER_FIRE.get(), particleX(living), particleY(living), particleZ(living),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void tickBalefire(LivingEntity living, ServerLevel level) {
        int ticks = balefireTicks(living);
        if (ticks <= 0) return;
        if (!living.isAlive()) {
            living.getPersistentData().remove(BALEFIRE);
            return;
        }
        living.getPersistentData().putInt(BALEFIRE, ticks - 1);
        RadiationSystem.contaminate(living, 5.0F, true);
        fizzle(living, level);
        damageWithoutKnockback(living, 20, 5.0F);
        level.sendParticles(ModParticles.FLAMETHROWER_BALEFIRE.get(), particleX(living), particleY(living), particleZ(living),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void fizzle(LivingEntity living, ServerLevel level) {
        if ((living.tickCount + living.getId()) % 15 != 0) return;
        level.playSound(null, living.getX(), living.getY() + living.getBbHeight() * 0.5D,
                living.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
                1.0F, 1.5F + living.getRandom().nextFloat() * 0.5F);
    }

    private static void damageWithoutKnockback(LivingEntity living, int interval, float damage) {
        if ((living.tickCount + living.getId()) % interval != 0) return;
        Vec3 motion = living.getDeltaMovement();
        living.invulnerableTime = 0;
        living.hurt(living.damageSources().onFire(), damage);
        living.setDeltaMovement(motion);
    }

    private static double particleX(LivingEntity living) {
        return living.getX() + (living.getRandom().nextDouble() - 0.5D) * living.getBbWidth();
    }

    private static double particleY(LivingEntity living) {
        return living.getY() + living.getRandom().nextDouble() * living.getBbHeight();
    }

    private static double particleZ(LivingEntity living) {
        return living.getZ() + (living.getRandom().nextDouble() - 0.5D) * living.getBbWidth();
    }
}
