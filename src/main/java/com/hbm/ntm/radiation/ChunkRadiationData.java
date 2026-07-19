package com.hbm.ntm.radiation;

import com.hbm.ntm.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Two-dimensional radiation soup, including chunks nobody is watching. */
public final class ChunkRadiationData {
    private static final float MAX_READ_RADIATION = 100_000.0F;
    private static final Map<ServerLevel, ChunkRadiationData> PER_LEVEL = new WeakHashMap<>();

    private final ServerLevel level;
    private final Map<Long, Float> radiation = new HashMap<>();

    private ChunkRadiationData(ServerLevel level) {
        this.level = level;
    }

    public static ChunkRadiationData get(ServerLevel level) {
        return PER_LEVEL.computeIfAbsent(level, ChunkRadiationData::new);
    }

    public static void unloadLevel(ServerLevel level) {
        PER_LEVEL.remove(level);
    }

    public void loadChunk(ChunkAccess chunk) {
        float persisted = sanitizeInternal(chunk.getData(ModAttachments.CHUNK_RADIATION));
        radiation.put(chunk.getPos().toLong(), persisted);
    }

    public void saveChunk(ChunkAccess chunk) {
        long key = chunk.getPos().toLong();
        chunk.setData(ModAttachments.CHUNK_RADIATION, sanitizeInternal(radiation.getOrDefault(key, 0.0F)));
        chunk.setUnsaved(true);
    }

    public float get(BlockPos pos) {
        return get(ChunkPos.asLong(pos));
    }

    public float get(long chunkPos) {
        return clampRead(radiation.getOrDefault(chunkPos, 0.0F));
    }

    public void set(BlockPos pos, float value) {
        set(ChunkPos.asLong(pos), value);
    }

    /** Explicit writes require a chunk that has shown up for work. */
    public void set(long chunkPos, float value) {
        int x = ChunkPos.getX(chunkPos);
        int z = ChunkPos.getZ(chunkPos);
        if (!level.hasChunk(x, z)) {
            return;
        }
        radiation.put(chunkPos, clampRead(value));
        persistLoaded(chunkPos);
    }

    public void increment(BlockPos pos, float amount) {
        if (Float.isFinite(amount) && amount != 0) {
            set(pos, get(pos) + amount);
        }
    }

    public void decrement(BlockPos pos, float amount) {
        if (Float.isFinite(amount) && amount != 0) {
            set(pos, get(pos) - amount);
        }
    }

    /** Spread once per second, including the bizarre absent-neighbor overwrite. */
    public void spreadAndDecay(ServerLevel ignored) {
        Map<Long, Float> previous = new HashMap<>(radiation);
        Map<Long, Float> next = new HashMap<>();

        for (Map.Entry<Long, Float> source : previous.entrySet()) {
            float sourceRadiation = sanitizeInternal(source.getValue());
            if (sourceRadiation == 0) {
                continue;
            }

            int sourceX = ChunkPos.getX(source.getKey());
            int sourceZ = ChunkPos.getZ(source.getKey());
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    int distanceType = Math.abs(offsetX) + Math.abs(offsetZ);
                    float percent = distanceType == 0 ? 0.6F : distanceType == 1 ? 0.075F : 0.025F;
                    long destination = ChunkPos.asLong(sourceX + offsetX, sourceZ + offsetZ);

                    if (previous.containsKey(destination)) {
                        float accumulated = next.getOrDefault(destination, 0.0F);
                        float value = (accumulated + sourceRadiation * percent) * 0.99F - 0.05F;
                        next.put(destination, Math.max(0.0F, value));
                    } else {
                        next.put(destination, sourceRadiation * percent);
                    }
                }
            }
        }

        Set<Long> changed = new HashSet<>(radiation.keySet());
        changed.addAll(next.keySet());
        radiation.clear();
        radiation.putAll(next);
        changed.forEach(this::persistLoaded);
    }

    public void clear() {
        Set<Long> previous = Set.copyOf(radiation.keySet());
        radiation.clear();
        previous.forEach(this::persistLoaded);
    }

    public int activeChunkCount() {
        return radiation.size();
    }

    private void persistLoaded(long chunkPos) {
        int x = ChunkPos.getX(chunkPos);
        int z = ChunkPos.getZ(chunkPos);
        if (!level.hasChunk(x, z)) {
            return;
        }
        LevelChunk chunk = level.getChunk(x, z);
        chunk.setData(ModAttachments.CHUNK_RADIATION,
                sanitizeInternal(radiation.getOrDefault(chunkPos, 0.0F)));
        chunk.setUnsaved(true);
    }

    private static float clampRead(float value) {
        return Math.min(MAX_READ_RADIATION, sanitizeInternal(value));
    }

    private static float sanitizeInternal(float value) {
        if (!Float.isFinite(value)) {
            return 0;
        }
        return Math.max(0, value);
    }
}
