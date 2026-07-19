package com.hbm.ntm.radiation;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.hazard.HazardHelper;
import com.hbm.ntm.hazard.HazardSystem;
import com.hbm.ntm.hazard.HazardTooltip;
import com.hbm.ntm.hazard.ContagionSystem;
import com.hbm.ntm.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class RadiationEvents {
    private static int spreadTimer;

    private RadiationEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RadiationEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RadiationEvents::onEntityTick);
        NeoForge.EVENT_BUS.addListener(RadiationEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(RadiationEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(RadiationEvents::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(RadiationEvents::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(RadiationEvents::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(RadiationEvents::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(RadiationEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(RadiationEvents::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(RadiationCommands::register);
    }

    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadiationSystem.tickPlayer(player);
        }
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity living && !(living instanceof ServerPlayer)) {
            RadiationSystem.tickNonPlayer(living);
        } else if (event.getEntity() instanceof ItemEntity itemEntity
                && itemEntity.tickCount % HbmConfig.ITEM_HAZARD_DROP_TICKRATE.get() == 0) {
            HazardSystem.updateDroppedItem(itemEntity);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!HbmConfig.ENABLE_CHUNK_RADIATION.get()) {
            return;
        }
        spreadTimer++;
        if (spreadTimer >= 20) {
            event.getServer().getAllLevels().forEach(level -> ChunkRadiationData.get(level).spreadAndDecay(level));
            spreadTimer = 0;
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        RadiationSystem.data(entity).setRadiation(0);
        if (entity instanceof ServerPlayer player) {
            player.syncData(ModAttachments.RADIATION);
        }
    }

    private static void onLivingDrops(LivingDropsEvent event) {
        if (!event.getEntity().level().isClientSide && ContagionSystem.isInfected(event.getEntity())) {
            event.getDrops().forEach(drop -> ContagionSystem.contaminate(drop.getItem()));
        }
    }

    private static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        event.setAmount(ContagionSystem.amplifyIncomingDamage(event.getEntity(), event.getAmount()));
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!HbmConfig.ENABLE_CHUNK_RADIATION.get() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        level.getServer().execute(() -> ChunkRadiationData.get(level).loadChunk(event.getChunk()));
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!HbmConfig.ENABLE_CHUNK_RADIATION.get() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Unload keeps the runtime coordinate. Accidents become compatibility eventually.
        ChunkRadiationData.get(level).saveChunk(event.getChunk());
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkRadiationData.unloadLevel(level);
        }
    }

    private static void onItemTooltip(ItemTooltipEvent event) {
        // Custom HBM carriers append their own tooltip. This path covers direct
        // registrations such as vanilla gunpowder, TNT, and pumpkin pie.
        if (!(event.getItemStack().getItem() instanceof HazardCarrier)) {
            HazardTooltip.append(event.getToolTip(), HazardHelper.get(event.getItemStack()), event.getItemStack());
        }
    }
}
