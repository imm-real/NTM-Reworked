package com.hbm.ntm.energy;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class HeEnergyEvents {
    private HeEnergyEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HeEnergyEvents::onServerTick);
    }

    private static void onServerTick(ServerTickEvent.Pre event) {
        event.getServer().getAllLevels().forEach(level -> HeNetworkManager.get(level).tick());
    }
}
