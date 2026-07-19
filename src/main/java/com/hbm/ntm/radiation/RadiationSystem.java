package com.hbm.ntm.radiation;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.armor.ArmorModHandler;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.hazard.HazardSystem;
import com.hbm.ntm.hazard.ContagionSystem;
import com.hbm.ntm.network.VomitPayload;
import com.hbm.ntm.registry.ModAttachments;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Iterator;
import java.util.Random;

public final class RadiationSystem {
    private static final ResourceLocation DIGAMMA_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "digamma");

    private RadiationSystem() {
    }

    public static RadiationData data(LivingEntity entity) {
        return entity.getData(ModAttachments.RADIATION);
    }

    public static void tickPlayer(ServerPlayer player) {
        RadiationData data = data(player);
        beginTick(player, data);
        HazardSystem.updatePlayerInventory(player);
        exposeFromEnvironment(player);
        ContagionSystem.tick(player);
        applyTransformations(player);
        applySickness(player);
        handleRadiationFx(player);
        handleLungDisease(player);
        finishTick(player, data);
    }

    public static void tickNonPlayer(LivingEntity entity) {
        RadiationData data = data(entity);
        beginTick(entity, data);
        HazardSystem.updateLivingInventory(entity);
        exposeFromEnvironment(entity);
        ContagionSystem.tick(entity);
        applyTransformations(entity);
        applySickness(entity);
        handleRadiationFx(entity);
        handleLungDisease(entity);
        finishTick(entity, data);
    }

    private static void beginTick(LivingEntity entity, RadiationData data) {
        if (entity.tickCount % 20 == 0) {
            data.finishExposureInterval();
        }
        data.tickMedicine();
        tickContamination(entity, data);
        updateDigammaModifier(entity, data.digamma());
    }

    private static void finishTick(LivingEntity entity, RadiationData data) {
        if (entity instanceof ServerPlayer player) {
            player.syncData(ModAttachments.RADIATION);
        }
    }

    private static void tickContamination(LivingEntity entity, RadiationData data) {
        Iterator<RadiationData.ContaminationEffect> iterator = data.contamination().iterator();
        while (iterator.hasNext()) {
            RadiationData.ContaminationEffect effect = iterator.next();
            contaminate(entity, effect.emission(), effect.bypassResistance());
            if (effect.tickDown()) {
                iterator.remove();
            }
        }
    }

    public static void exposeFromEnvironment(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = entity.blockPosition();
        float radiationPerSecond = HbmConfig.ENABLE_CHUNK_RADIATION.get()
                ? ChunkRadiationData.get(level).get(pos)
                : 0.0F;
        if (level.dimension() == Level.NETHER) {
            radiationPerSecond = Math.max(radiationPerSecond, HbmConfig.NETHER_RADIATION.get().floatValue());
        }
        if (radiationPerSecond > 0) {
            contaminate(entity, radiationPerSecond / 20.0F, false);
        }
    }

    /** Logs the exposure before checking whether the victim gets to care. */
    public static boolean contaminate(LivingEntity entity, float amount, boolean bypassResistance) {
        if (!Float.isFinite(amount) || amount <= 0) {
            return false;
        }
        RadiationData data = data(entity);
        data.addEnvironmentalRadiation(amount);

        if (!HbmConfig.ENABLE_CONTAMINATION.get()) {
            return false;
        }
        if (entity instanceof Player player) {
            if (player.isCreative() || player.tickCount < 200) {
                return false;
            }
        }
        if (isRadiationImmune(entity)) {
            return false;
        }

        float accepted = amount;
        if (!bypassResistance) {
            accepted *= (float) Math.pow(10.0D, -calculateResistance(entity));
        }
        data.addRadiation(accepted);
        return true;
    }

    /** Mobs never learned percentages. */
    public static float calculateResistance(LivingEntity entity) {
        if (!(entity instanceof Player)) {
            return 0;
        }
        float resistance = data(entity).medicineResistance();
        for (ItemStack armor : entity.getArmorSlots()) {
            if (armor.getItem() instanceof RadiationProtectiveItem protectiveItem) {
                resistance += Math.max(0, protectiveItem.hbm$getRadiationResistance(armor, entity));
            } else {
                resistance += vanillaArmorResistance(armor);
            }
            resistance += ArmorModHandler.claddingResistance(armor, entity.registryAccess());
        }
        return resistance;
    }

    private static float vanillaArmorResistance(ItemStack stack) {
        if (stack.is(net.minecraft.world.item.Items.IRON_HELMET) || stack.is(net.minecraft.world.item.Items.GOLDEN_HELMET)) return 0.0045F;
        if (stack.is(net.minecraft.world.item.Items.IRON_CHESTPLATE) || stack.is(net.minecraft.world.item.Items.GOLDEN_CHESTPLATE)) return 0.009F;
        if (stack.is(net.minecraft.world.item.Items.IRON_LEGGINGS) || stack.is(net.minecraft.world.item.Items.GOLDEN_LEGGINGS)) return 0.00675F;
        if (stack.is(net.minecraft.world.item.Items.IRON_BOOTS) || stack.is(net.minecraft.world.item.Items.GOLDEN_BOOTS)) return 0.00225F;
        return 0;
    }

    private static void applyTransformations(LivingEntity entity) {
        if (!HbmConfig.ENABLE_CONTAMINATION.get() || !(entity.level() instanceof ServerLevel level)
                || !entity.isAlive() || isRadiationImmune(entity)) {
            return;
        }
        float radiation = data(entity).radiation();

        if (entity instanceof Cow && !(entity instanceof MushroomCow) && radiation >= 50.0F) {
            MushroomCow replacement = EntityType.MOOSHROOM.create(level);
            if (replacement != null) {
                replacement.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                level.addFreshEntity(replacement);
                entity.discard();
            }
        } else if (entity instanceof Villager && radiation >= 500.0F) {
            Zombie replacement = EntityType.ZOMBIE.create(level);
            if (replacement != null) {
                replacement.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                level.addFreshEntity(replacement);
                entity.discard();
            }
        }
    }

    private static void applySickness(LivingEntity entity) {
        if (!HbmConfig.ENABLE_CONTAMINATION.get() || !entity.isAlive()
                || entity instanceof Player player && player.isCreative() || isRadiationImmune(entity)) {
            return;
        }

        float radiation = data(entity).radiation();
        if (radiation < 200.0F) {
            return;
        }

        if (radiation >= 1000.0F) {
            DamageSource source = entity.damageSources().source(ModDamageTypes.RADIATION);
            entity.hurt(source, 1000.0F);
            data(entity).setRadiation(0);
            if (entity.isAlive()) {
                entity.setHealth(0);
                entity.die(source);
            }
            return;
        }

        if (radiation >= 800.0F) {
            chance(entity, 300, MobEffects.CONFUSION, 150, 0);
            chance(entity, 300, MobEffects.MOVEMENT_SLOWDOWN, 200, 2);
            chance(entity, 300, MobEffects.WEAKNESS, 200, 2);
            chance(entity, 500, MobEffects.POISON, 60, 2);
            chance(entity, 700, MobEffects.WITHER, 60, 1);
        } else if (radiation >= 600.0F) {
            chance(entity, 300, MobEffects.CONFUSION, 150, 0);
            chance(entity, 300, MobEffects.MOVEMENT_SLOWDOWN, 200, 2);
            chance(entity, 300, MobEffects.WEAKNESS, 200, 2);
            chance(entity, 500, MobEffects.POISON, 60, 1);
        } else if (radiation >= 400.0F) {
            chance(entity, 300, MobEffects.CONFUSION, 150, 0);
            chance(entity, 500, MobEffects.MOVEMENT_SLOWDOWN, 100, 0);
            chance(entity, 300, MobEffects.WEAKNESS, 100, 1);
        } else {
            chance(entity, 300, MobEffects.CONFUSION, 100, 0);
            chance(entity, 500, MobEffects.WEAKNESS, 100, 0);
        }
    }

    private static void handleRadiationFx(LivingEntity entity) {
        if (!HbmConfig.ENABLE_CONTAMINATION.get()
                || !(entity.level() instanceof ServerLevel level)
                || !entity.isAlive()
                || entity instanceof Player player && player.isCreative()
                || isRadiationImmune(entity)
                || entity.getType().getCategory() == MobCategory.WATER_CREATURE) {
            return;
        }

        float radiation = data(entity).radiation();
        Random phaseRandom = new Random(entity.getId());
        int bloodOffset = phaseRandom.nextInt(600);
        int normalOffset = phaseRandom.nextInt(1200);

        if (radiation > 600.0F) {
            int phase = (int) ((level.getGameTime() + bloodOffset) % 600L);
            if (phase < 20) {
                spawnVomit(level, entity, true, 25);
                if (phase == 1) {
                    beginVomitBurst(level, entity);
                }
            }
        } else if (radiation > 200.0F) {
            int phase = (int) ((level.getGameTime() + normalOffset) % 1200L);
            if (phase < 20) {
                spawnVomit(level, entity, false, 15);
                if (phase == 1) {
                    beginVomitBurst(level, entity);
                }
            }
        }
    }

    private static void spawnVomit(ServerLevel level, LivingEntity entity, boolean blood, int count) {
        PacketDistributor.sendToPlayersNear(
                level,
                null,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                25.0D,
                new VomitPayload(entity.getId(), blood, count)
        );
    }

    private static void beginVomitBurst(ServerLevel level, LivingEntity entity) {
        level.playSound(
                null,
                entity.blockPosition(),
                ModSounds.PLAYER_VOMIT.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
        entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 19));
    }

    public static void addAsbestos(LivingEntity entity, int amount) {
        if (amount <= 0 || HbmConfig.DISABLE_ASBESTOS.get()) {
            return;
        }
        RadiationData data = data(entity);
        data.setAsbestos(data.asbestos() + amount);
        if (data.asbestos() >= RadiationData.MAX_ASBESTOS) {
            data.setAsbestos(0);
            killWith(entity, ModDamageTypes.ASBESTOS);
        }
    }

    public static void addBlackLung(LivingEntity entity, int amount) {
        if (amount <= 0 || HbmConfig.DISABLE_COAL_DUST.get()) {
            return;
        }
        RadiationData data = data(entity);
        data.setBlackLung(data.blackLung() + amount);
        if (data.blackLung() >= RadiationData.MAX_BLACK_LUNG) {
            data.setBlackLung(0);
            killWith(entity, ModDamageTypes.BLACK_LUNG);
        }
    }

    private static void handleLungDisease(LivingEntity entity) {
        RadiationData data = data(entity);
        if (entity instanceof Player player && player.isCreative()) {
            data.setAsbestos(0);
            data.setBlackLung(0);
            return;
        }

        if (!HbmConfig.DISABLE_COAL_DUST.get()
                && data.blackLung() > 0
                && data.blackLung() < RadiationData.MAX_BLACK_LUNG / 2) {
            data.setBlackLung(data.blackLung() - 1);
        }

        double blackLung = HbmConfig.DISABLE_COAL_DUST.get() ? 0
                : Math.min(data.blackLung(), RadiationData.MAX_BLACK_LUNG);
        double asbestos = HbmConfig.DISABLE_ASBESTOS.get() ? 0
                : Math.min(data.asbestos(), RadiationData.MAX_ASBESTOS);
        double blackLungDelta = 1.0D - blackLung / RadiationData.MAX_BLACK_LUNG;
        double asbestosDelta = 1.0D - asbestos / RadiationData.MAX_ASBESTOS;
        double total = 1.0D - blackLungDelta * asbestosDelta;

        if (total > 0.75D) {
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
        }
        if (total > 0.95D) {
            entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
        }
    }

    public static void addDigamma(LivingEntity entity, float amount) {
        if (!Float.isFinite(amount) || amount <= 0 || isDigammaImmune(entity)) {
            return;
        }
        RadiationData data = data(entity);
        data.addDigamma(amount);
        updateDigammaModifier(entity, data.digamma());
        if (data.digamma() >= RadiationData.MAX_DIGAMMA) {
            killWith(entity, ModDamageTypes.DIGAMMA);
        }
    }

    private static void updateDigammaModifier(LivingEntity entity, float digamma) {
        var attribute = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(DIGAMMA_MODIFIER);
        if (digamma > 0) {
            double healthModifier = Math.pow(0.5D, digamma) - 1.0D;
            attribute.addOrUpdateTransientModifier(new AttributeModifier(
                    DIGAMMA_MODIFIER,
                    healthModifier,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
            if (entity.getHealth() > entity.getMaxHealth()) {
                entity.setHealth(entity.getMaxHealth());
            }
        }
    }

    private static void killWith(LivingEntity entity, net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> type) {
        DamageSource source = entity.damageSources().source(type);
        entity.hurt(source, 1000.0F);
        if (entity.isAlive()) {
            entity.setHealth(0);
            entity.die(source);
        }
    }

    private static void chance(
            LivingEntity entity,
            int oneIn,
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
            int duration,
            int amplifier
    ) {
        if (entity.getRandom().nextInt(oneIn) == 0) {
            entity.addEffect(new MobEffectInstance(effect, duration, amplifier));
        }
    }

    public static boolean isRadiationImmune(LivingEntity entity) {
        return entity instanceof RadiationImmune
                || entity instanceof MushroomCow
                || entity instanceof Zombie
                || entity instanceof AbstractSkeleton
                || entity instanceof Ocelot;
    }

    private static boolean isDigammaImmune(LivingEntity entity) {
        return entity instanceof Ocelot
                || entity instanceof Player player && (player.isCreative() || player.tickCount < 200);
    }

    public interface RadiationImmune {
    }
}
