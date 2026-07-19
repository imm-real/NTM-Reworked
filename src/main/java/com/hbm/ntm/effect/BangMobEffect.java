package com.hbm.ntm.effect;

import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.radiation.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Delayed B93 beam kill effect from HbmPotion.bang. */
public final class BangMobEffect extends MobEffect {
    public BangMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x111111);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration <= 10;
    }

    @Override
    public boolean applyEffectTick(LivingEntity living, int amplifier) {
        if (living.level().isClientSide) return true;
        living.hurt(living.damageSources().source(ModDamageTypes.BANG), 1_000.0F);
        living.setHealth(0.0F);
        if (!(living instanceof Player)) living.discard();
        living.level().playSound(null, living.blockPosition(), ModSounds.LASER_BANG.get(),
                SoundSource.PLAYERS, 100.0F, 1.0F);
        if (living.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.EXPLOSION, living.getX(), living.getY() + living.getBbHeight() * 0.5D,
                    living.getZ(), 10, 0.5D, 0.5D, 0.5D, 0.05D);
        }
        if (living instanceof Cow cow) {
            cow.spawnAtLocation(new ItemStack(com.hbm.ntm.registry.ModItems.CHEESE.get(),
                    cow.isBaby() ? 10 : 3), 1.0F);
        }
        return true;
    }
}
