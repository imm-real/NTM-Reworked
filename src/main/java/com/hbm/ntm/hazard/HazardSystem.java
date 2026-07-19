package com.hbm.ntm.hazard;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.radiation.RadiationSystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Applies item hazards, multiplied by however much trouble is in the stack. */
public final class HazardSystem {
    private HazardSystem() {
    }

    public static void updatePlayerInventory(Player player) {
        Inventory inventory = player.getInventory();
        inventory.items.forEach(stack -> applyToHolder(stack, player));
        inventory.armor.forEach(stack -> applyToHolder(stack, player));
        inventory.offhand.forEach(stack -> applyToHolder(stack, player));
    }

    public static void updateLivingInventory(LivingEntity entity) {
        entity.getAllSlots().forEach(stack -> applyToHolder(stack, entity));
    }

    public static void updateDroppedItem(ItemEntity entity) {
        if (entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) {
            return;
        }
        HazardProfile hazards = HazardHelper.get(stack);

        if (hazards.hydroactive() > 0 && !HbmConfig.DISABLE_HYDROACTIVE.get()
                && (entity.isInWaterOrRain() || entity.level().getFluidState(entity.blockPosition()).is(net.minecraft.tags.FluidTags.WATER))) {
            explodeDroppedStack(entity, hazards.hydroactive());
            return;
        }
        if (hazards.explosive() > 0 && !HbmConfig.DISABLE_EXPLOSIVE.get() && entity.isOnFire()) {
            explodeDroppedStack(entity, hazards.explosive());
        }
    }

    public static void applyToHolder(ItemStack stack, LivingEntity entity) {
        if (stack.isEmpty() || entity.level().isClientSide) {
            return;
        }
        HazardProfile hazards = HazardHelper.get(stack);
        if (hazards.isEmpty()) {
            return;
        }

        if (hazards.radiation() > 0) {
            float radiation = hazards.radiation() * stack.getCount() / 20.0F;
            if (entity instanceof Player player && hasReacher(player)) {
                radiation = reacherRadiation(radiation, HbmConfig.ENABLE_528.get());
            }
            RadiationSystem.contaminate(entity, radiation, false);
        }

        if (hazards.heat() > 0 && !HbmConfig.DISABLE_HOT.get() && !entity.isInWaterOrRain()
                && !(entity instanceof Player player
                && reacherBlocksHeat(hasReacher(player), HbmConfig.ENABLE_528.get()))) {
            entity.igniteForSeconds((float) Math.ceil(hazards.heat()));
        }

        if (hazards.hydroactive() > 0 && !HbmConfig.DISABLE_HYDROACTIVE.get() && entity.isInWaterOrRain()) {
            stack.setCount(0);
            explode(entity, hazards.hydroactive());
            return;
        }

        if (hazards.explosive() > 0 && !HbmConfig.DISABLE_EXPLOSIVE.get() && entity.isOnFire()) {
            stack.setCount(0);
            explode(entity, hazards.explosive());
            return;
        }

        if (hazards.coalDust() > 0 && !HbmConfig.DISABLE_COAL_DUST.get()) {
            int amount = (int) Math.min(hazards.coalDust() * stack.getCount(), 10.0F);
            if (!hasProtection(entity, HazardProtection.PARTICLE_COARSE, 0)) {
                RadiationSystem.addBlackLung(entity, amount);
            } else if (entity.getRandom().nextInt(coalFilterWearDenominator(stack.getCount())) == 0) {
                damageProtection(entity, HazardProtection.PARTICLE_COARSE, (int) hazards.coalDust());
            }
        }

        if (hazards.asbestos() > 0 && !HbmConfig.DISABLE_ASBESTOS.get()) {
            int amount = (int) Math.min(hazards.asbestos(), 10.0F);
            if (!hasProtection(entity, HazardProtection.PARTICLE_FINE, amount)) {
                RadiationSystem.addAsbestos(entity, amount);
            }
        }

        if (hazards.blinding() > 0 && !HbmConfig.DISABLE_BLINDING.get()
                && !hasProtection(entity, HazardProtection.LIGHT, 0)) {
            entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, (int) Math.ceil(hazards.blinding()), 0));
        }

        if (hazards.digamma() > 0) {
            RadiationSystem.addDigamma(entity, hazards.digamma() / 20.0F);
        }
    }

    public static boolean hasProtection(LivingEntity entity, HazardProtection protection, int damage) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof HazardProtectiveItem protective)
                || !protective.hbm$protects(head, entity, protection)) {
            return false;
        }
        if (damage > 0) protective.hbm$damageProtection(head, entity, protection, damage);
        return true;
    }

    static int coalFilterWearDenominator(int stackCount) {
        return Math.max(65 - stackCount, 1);
    }

    static float reacherRadiation(float radiation, boolean mode528) {
        if (mode528) return radiation / 49.0F;
        double shifted = radiation + 2.0D;
        return (float) (Math.sqrt(radiation + 1.0D / (shifted * shifted)) - 1.0D / shifted);
    }

    static boolean reacherBlocksHeat(boolean hasReacher, boolean mode528) {
        return hasReacher && !mode528;
    }

    private static boolean hasReacher(Player player) {
        return player.getInventory().items.stream().anyMatch(stack -> stack.is(com.hbm.ntm.registry.ModItems.REACHER.get()));
    }

    private static void damageProtection(LivingEntity entity, HazardProtection protection, int damage) {
        if (damage <= 0) return;
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof HazardProtectiveItem protective
                && protective.hbm$protects(head, entity, protection)) {
            protective.hbm$damageProtection(head, entity, protection, damage);
        }
    }

    private static void explode(LivingEntity entity, float strength) {
        if (entity.level() instanceof ServerLevel level) {
            level.explode(null, entity.getX(), entity.getEyeY(), entity.getZ(), strength, false, Level.ExplosionInteraction.TNT);
        }
    }

    private static void explodeDroppedStack(ItemEntity entity, float strength) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5D;
        double z = entity.getZ();
        entity.discard();
        if (entity.level() instanceof ServerLevel level) {
            level.explode(null, x, y, z, strength, false, Level.ExplosionInteraction.TNT);
        }
    }
}
