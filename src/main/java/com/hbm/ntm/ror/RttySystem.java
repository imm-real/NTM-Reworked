package com.hbm.ntm.ror;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

/** The radio station is one tick late. This is intentional; even redstone has paperwork. */
public final class RttySystem {
    public record Message(String value, long tick) {}

    private static final Map<ResourceKey<Level>, Map<String, Message>> AIR = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<String, String>> OUTBOX = new HashMap<>();

    private RttySystem() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RttySystem::tick);
        NeoForge.EVENT_BUS.addListener(RttySystem::unload);
    }

    public static void broadcast(ServerLevel level, String channel, String value) {
        if (channel == null || channel.isEmpty() || value == null) return;
        OUTBOX.computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .merge(channel, value, RttySystem::combine);
    }

    public static Message listen(ServerLevel level, String channel) {
        return AIR.getOrDefault(level.dimension(), Map.of()).get(channel);
    }

    private static String combine(String first, String second) {
        try {
            return Long.toString(Math.addExact(Long.parseLong(first), Long.parseLong(second)));
        } catch (NumberFormatException | ArithmeticException ignored) {
            return second;
        }
    }

    private static void tick(ServerTickEvent.Pre event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Map<String, String> queued = OUTBOX.remove(level.dimension());
            if (queued == null || queued.isEmpty()) continue;
            Map<String, Message> live = AIR.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
            long tick = level.getGameTime();
            queued.forEach((channel, value) -> live.put(channel, new Message(value, tick)));
        }
    }

    private static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        AIR.remove(level.dimension());
        OUTBOX.remove(level.dimension());
    }
}
