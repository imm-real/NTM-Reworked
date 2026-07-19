package com.hbm.ntm.hazard;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.network.VomitPayload;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.radiation.RadiationData;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

/** MKU contagion. Wash your hands after reading. */
public final class ContagionSystem {
    public static final String CONTAMINATED_ITEM_TAG = "ntmContagion";
    public static final int CONTAGION_TICKS = 3 * 60 * 60 * 20;
    public static final int TRANSMISSION_START = CONTAGION_TICKS - 5 * 60 * 20;

    private static final String[] HAZ2_SUIT_PREFIXES = {
            "hazmat_paa", "liquidator", "euphemium", "rpa", "fau", "dns"
    };

    private ContagionSystem() {
    }

    public static void tick(LivingEntity entity) {
        if (!HbmConfig.ENABLE_MKU.get() || entity.level().isClientSide) return;

        RadiationData data = RadiationSystem.data(entity);
        int contagion = data.contagion();

        // Pocket inspection happens before the disease advances. The stale value stays.
        if (entity instanceof Player player) tickCarriedContamination(player, contagion);
        if (contagion <= 0) return;

        data.setContagion(contagion - 1);

        if (contagion < TRANSMISSION_START && contagion % 20 == 0) {
            transmit(entity);
        }

        if (contagion < 2 * 60 * 60 * 20 && entity.getRandom().nextInt(1000) == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20, 0));
        }
        if (contagion < 60 * 60 * 20 && entity.getRandom().nextInt(100) == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 4));
        }
        if (contagion < 30 * 60 * 20 && entity.getRandom().nextInt(400) == 0) {
            entity.hurt(entity.damageSources().source(ModDamageTypes.MKU), 1.0F);
        }
        if (contagion < 5 * 60 * 20 && entity.getRandom().nextInt(100) == 0) {
            entity.hurt(entity.damageSources().source(ModDamageTypes.MKU), 2.0F);
        }

        int vomitPhase = (contagion + entity.getId()) % 200;
        if (contagion < 30 * 60 * 20 && vomitPhase < 20 && canVomit(entity)
                && entity.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayersNear(
                    level, null, entity.getX(), entity.getY(), entity.getZ(), 25.0D,
                    new VomitPayload(entity.getId(), true, 25)
            );
            if (vomitPhase == 19) {
                level.playSound(null, entity.blockPosition(), ModSounds.PLAYER_VOMIT.get(),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }

        // contagion is already positive here. The later zero branch is dead and staying dead.
    }

    public static void infect(LivingEntity entity) {
        RadiationSystem.data(entity).setContagion(CONTAGION_TICKS);
    }

    public static boolean isInfected(LivingEntity entity) {
        return HbmConfig.ENABLE_MKU.get() && RadiationSystem.data(entity).contagion() > 0;
    }

    public static float amplifyIncomingDamage(LivingEntity entity, float amount) {
        return isInfected(entity) && amount < 100.0F ? amount * 2.0F : amount;
    }

    public static void contaminate(ItemStack stack) {
        if (stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putBoolean(CONTAMINATED_ITEM_TAG, true));
    }

    public static boolean isContaminated(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(CONTAMINATED_ITEM_TAG);
    }

    /** Haz2 suit plus bacteria protection. No mixing and matching apocalypse outfits. */
    public static boolean hasCompleteProtection(LivingEntity entity) {
        return hasHaz2Suit(entity) && HazardSystem.hasProtection(entity, HazardProtection.BACTERIA, 0);
    }

    private static void tickCarriedContamination(Player player, int contagionAtTickStart) {
        int slot = player.getRandom().nextInt(player.getInventory().items.size());
        ItemStack selected = player.getInventory().items.get(slot);
        if (player.getRandom().nextInt(100) == 0) {
            selected = player.getInventory().armor.get(player.getRandom().nextInt(4));
        }
        if (selected.isEmpty() || selected.getMaxStackSize() != 1) return;

        if (contagionAtTickStart > 0) {
            contaminate(selected);
        } else if (isContaminated(selected) && !hasCompleteProtection(player)) {
            infect(player);
        }
    }

    private static void transmit(LivingEntity source) {
        double range = source.isInWaterOrRain() ? 16.0D : 2.0D;
        for (Entity nearby : source.level().getEntities(source, source.getBoundingBox().inflate(range))) {
            if (nearby instanceof LivingEntity living) {
                if (RadiationSystem.data(living).contagion() <= 0 && !hasCompleteProtection(living)) {
                    infect(living);
                }
            } else if (nearby instanceof ItemEntity item) {
                contaminate(item.getItem());
            }
        }
    }

    private static boolean hasHaz2Suit(LivingEntity entity) {
        for (String prefix : HAZ2_SUIT_PREFIXES) {
            if (isHbmItem(entity.getItemBySlot(EquipmentSlot.HEAD), prefix + "_helmet")
                    && isHbmItem(entity.getItemBySlot(EquipmentSlot.CHEST), prefix + "_plate")
                    && isHbmItem(entity.getItemBySlot(EquipmentSlot.LEGS), prefix + "_legs")
                    && isHbmItem(entity.getItemBySlot(EquipmentSlot.FEET), prefix + "_boots")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHbmItem(ItemStack stack, String path) {
        if (stack.isEmpty()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("hbm") && id.getPath().equals(path);
    }

    private static boolean canVomit(LivingEntity entity) {
        MobCategory category = entity.getType().getCategory();
        return category != MobCategory.WATER_CREATURE
                && category != MobCategory.WATER_AMBIENT
                && category != MobCategory.UNDERGROUND_WATER_CREATURE
                && category != MobCategory.AXOLOTLS;
    }
}
