package com.hbm.ntm.world;

import com.hbm.ntm.network.MoonStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keeps the persistent moon state synchronized across login, respawn, and dimension travel. */
public final class MoonDestructionEvents {
    private MoonDestructionEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MoonDestructionEvents::playerLoggedIn);
        NeoForge.EVENT_BUS.addListener(MoonDestructionEvents::playerRespawned);
        NeoForge.EVENT_BUS.addListener(MoonDestructionEvents::playerChangedDimension);
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
        boolean destroyed = MoonDestructionData.get(serverPlayer.serverLevel()).isDestroyed();
        PacketDistributor.sendToPlayer(serverPlayer, new MoonStatePayload(destroyed, false));
    }
}
