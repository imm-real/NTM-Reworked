package com.hbm.ntm.pollution;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.config.HbmConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class PollutionEvents {
    private static final ResourceLocation HEALTH = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "soot_health");
    private static final ResourceLocation DAMAGE = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "soot_damage");
    private static int timer;

    private PollutionEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PollutionEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(PollutionEvents::onFinalizeSpawn);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!HbmConfig.ENABLE_POLLUTION.get()) {
            return;
        }
        timer++;
        if (timer >= 60) {
            event.getServer().getAllLevels().forEach(level -> PollutionData.get(level).spreadAndDecay());
            timer = 0;
        }
    }

    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!HbmConfig.ENABLE_POLLUTION.get() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Enemy)) {
            return;
        }
        float soot = PollutionData.get(level).get(event.getEntity().blockPosition(), PollutionData.Type.SOOT);
        if (soot <= HbmConfig.SOOT_MOB_THRESHOLD.get()) {
            return;
        }
        var health = event.getEntity().getAttribute(Attributes.MAX_HEALTH);
        if (health != null && !health.hasModifier(HEALTH)) {
            health.addPermanentModifier(new AttributeModifier(HEALTH, 1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        var damage = event.getEntity().getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && !damage.hasModifier(DAMAGE)) {
            damage.addPermanentModifier(new AttributeModifier(DAMAGE, 1.5D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        event.getEntity().setHealth(event.getEntity().getMaxHealth());
    }
}
