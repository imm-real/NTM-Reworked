package com.hbm.ntm.armor;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.EnvsuitArmorItem;
import com.hbm.ntm.radiation.ModDamageTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** M1TTY suit bonuses and its opinions about incoming damage. */
public final class EnvsuitArmorEvents {
    public static final ResourceLocation SPRINT_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "envsuit_sprint_speed");

    private EnvsuitArmorEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EnvsuitArmorEvents::onPlayerTick);
        // MKU meddles first, suit second, vanilla last.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EnvsuitArmorEvents::onIncomingDamage);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        applyTick(event.getEntity());
    }

    /** Test entrance that avoids ringing the global event bell. */
    public static void applyTick(Player player) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (!player.level().isClientSide && speed != null) {
            speed.removeModifier(SPRINT_SPEED_MODIFIER);
        }

        if (!player.isAlive() || !EnvsuitArmorItem.hasFullPoweredSet(player)) return;

        if (!player.level().isClientSide) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20, 0, true, true));
            if (player.isSprinting() && speed != null) {
                speed.addOrUpdateTransientModifier(new AttributeModifier(
                        SPRINT_SPEED_MODIFIER, 0.1D, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        if (player.isInWater()) {
            applyWaterBonuses(player);
        } else if (!player.level().isClientSide) {
            // TODO helmet night-vision mod; borrowed night vision gets confiscated
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    static void applyWaterBonuses(Player player) {
        if (!player.level().isClientSide) {
            player.setAirSupply(300);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 15 * 20, 0));
        }

        double thrust = 0.1D * player.zza;
        if (thrust != 0.0D) {
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(player.getDeltaMovement().add(look.scale(thrust)));
        }
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        event.setAmount(applyDamageTable(event.getEntity(), event.getSource(), event.getAmount()));
    }

    /** M1TTY damage table, including its dislike of electricity. */
    public static float applyDamageTable(LivingEntity wearer, DamageSource source, float amount) {
        float adjusted = amount;
        if (source.is(ModDamageTypes.ELECTRIC) && EnvsuitArmorItem.hasPoweredChest(wearer)) {
            adjusted *= 5.0F;
        }

        if (!EnvsuitArmorItem.hasFullSet(wearer) || source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            return adjusted;
        }

        float threshold;
        float resistance;
        if (source.is(DamageTypes.DROWN)) {
            threshold = 0.0F;
            resistance = 1.0F;
        } else if (source.is(DamageTypes.FALL)) {
            threshold = 5.0F;
            resistance = 0.75F;
        } else if (source.is(DamageTypeTags.IS_FIRE)) {
            threshold = 2.0F;
            resistance = 0.75F;
        } else {
            // Unblockable damage declines the suit's discount coupon.
            if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return adjusted;
            threshold = 0.0F;
            resistance = 0.1F;
        }

        return Math.max(0.0F, adjusted - threshold) * (1.0F - resistance);
    }
}
