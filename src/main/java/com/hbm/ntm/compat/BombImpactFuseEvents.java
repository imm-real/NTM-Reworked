package com.hbm.ntm.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runs queued impact detonations after moving-sublevel physics has released its native locks. */
public final class BombImpactFuseEvents {
    private static final Map<PendingImpact, Runnable> PENDING = new LinkedHashMap<>();

    private BombImpactFuseEvents() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BombImpactFuseEvents::onServerTick);
    }

    public static void queue(ServerLevel level, BlockPos position, Runnable action) {
        PENDING.putIfAbsent(new PendingImpact(level, position.immutable()), action);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;

        Map<PendingImpact, Runnable> impacts = new LinkedHashMap<>(PENDING);
        PENDING.clear();
        impacts.forEach((impact, action) -> {
            if (impact.level().getServer() == event.getServer()) action.run();
        });
    }

    private record PendingImpact(ServerLevel level, BlockPos position) { }
}
