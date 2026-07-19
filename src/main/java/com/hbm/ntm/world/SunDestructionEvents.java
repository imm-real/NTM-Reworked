package com.hbm.ntm.world;

import com.hbm.ntm.network.SunStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keeps the persistent solar state synchronized across login, respawn, and dimension travel. */
public final class SunDestructionEvents {
    private SunDestructionEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SunDestructionEvents::playerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SunDestructionEvents::playerRespawned);
        NeoForge.EVENT_BUS.addListener(SunDestructionEvents::playerChangedDimension);
    }

    private static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    private static void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
    }

    private static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    private static void sync(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        boolean destroyed = SunDestructionData.get(serverPlayer.serverLevel()).isDestroyed();
        PacketDistributor.sendToPlayer(serverPlayer, new SunStatePayload(destroyed, false));
    }
}
