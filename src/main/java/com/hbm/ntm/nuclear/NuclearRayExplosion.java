package com.hbm.ntm.nuclear;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Many rays enter, terrain leaves. */
public final class NuclearRayExplosion {
    private static final int FLUID_SETTLE_PASSES = 10;
    private final ServerLevel level;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int strength;
    private final int speed;
    private final int length;
    private final int rayCount;
    private final Map<Long, List<Tip>> perChunk = new HashMap<>();
    private final List<Long> orderedChunks = new ArrayList<>();
    private final LongArrayList rayChunks = new LongArrayList();
    private final BlockPos.MutableBlockPos tracePos = new BlockPos.MutableBlockPos();
    private int ray = 1;
    private int nextChunk;
    private long activeChunkKey;
    private List<Tip> activeTips;
    private int activeTipIndex;
    private int activeChunkX;
    private int activeChunkZ;
    private int activeEnter;
    private LongOpenHashSet activeRemove;
    private LongOpenHashSet activeTipBlocks;
    private LongIterator activeRemovalIterator;
    private final List<Long> fluidChunks = new ArrayList<>();
    private int fluidChunkIndex;
    private int fluidSectionIndex;
    private int fluidBlockIndex;
    private int fluidCleanupPasses;
    private double latitude = Math.PI;
    private double longitude;
    private boolean cacheComplete;

    public NuclearRayExplosion(ServerLevel level, int centerX, int centerY, int centerZ,
                               int strength, int speed, int length) {
        this.level = level;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.strength = strength;
        this.speed = speed;
        this.length = length;
        this.rayCount = (int) (2.5D * Math.PI * strength * strength);
    }

    /** Predictable serving size for tests. */
    public void cacheTick() {
        if (!cacheComplete) collectTips(speed * 10, Long.MAX_VALUE);
    }

    /** Collect rays until the tick budget starts making threatening noises. */
    public void cacheTick(int milliseconds) {
        if (cacheComplete || milliseconds <= 0) return;
        long deadline = System.nanoTime() + milliseconds * 1_000_000L;
        collectTips(Integer.MAX_VALUE, deadline);
    }

    private void collectTips(int count, long deadline) {
        int processed = 0;
        int maxSteps = Math.min(Mth.ceil(strength), length);
        while (ray <= rayCount) {
            double dx = Math.sin(latitude) * Math.cos(longitude);
            double dz = Math.sin(latitude) * Math.sin(longitude);
            double dy = Math.cos(latitude);
            float resistance = strength;
            Tip last = null;
            rayChunks.clear();
            long lastChunk = Long.MIN_VALUE;

            for (int i = 0; i < maxSteps; i++) {
                float x = (float) (centerX + dx * i);
                float y = (float) (centerY + dy * i);
                float z = (float) (centerZ + dz * i);
                int blockY = Mth.floor(y);
                // Past the world is still past the world.
                if (blockY < level.getMinBuildHeight() && dy < 0.0D
                        || blockY >= level.getMaxBuildHeight() && dy > 0.0D) break;
                tracePos.set(Mth.floor(x), blockY, Mth.floor(z));
                BlockState state = level.getBlockState(tracePos);
                boolean air = state.isAir();
                if (!air && state.getFluidState().isEmpty()) {
                    double factor = 100.0D - (double) i / strength * 100.0D;
                    factor *= 0.07D;
                    resistance -= (float) Math.pow(masqueradeResistance(state), 7.5D - factor);
                }
                if (resistance > 0.0F && !air) {
                    last = new Tip(x, y, z);
                    long chunk = ChunkPos.asLong(tracePos);
                    // Straight rays do not make U-turns between chunks.
                    if (chunk != lastChunk) {
                        rayChunks.add(chunk);
                        lastChunk = chunk;
                    }
                }
                if (resistance <= 0.0F || i + 1 >= length || i == Mth.ceil(strength) - 1) break;
            }

            if (last != null) {
                for (int i = 0; i < rayChunks.size(); i++) {
                    perChunk.computeIfAbsent(rayChunks.getLong(i), ignored -> new ArrayList<>()).add(last);
                }
            }
            advanceSpiral();
            processed++;
            if (processed >= count || (processed & 15) == 0 && System.nanoTime() >= deadline) return;
        }

        orderedChunks.addAll(perChunk.keySet());
        int chunkX = centerX >> 4;
        int chunkZ = centerZ >> 4;
        orderedChunks.sort(Comparator.comparingInt(key ->
                Math.abs(chunkX - ChunkPos.getX(key)) + Math.abs(chunkZ - ChunkPos.getZ(key))));
        prepareFluidCleanupChunks();
        cacheComplete = true;
    }

    private void advanceSpiral() {
        if (ray < rayCount) {
            int k = ray + 1;
            double h = -1.0D + 2.0D * (k - 1.0D) / (rayCount - 1.0D);
            latitude = Math.acos(h);
            longitude = (longitude + 3.6D / Math.sqrt(rayCount) / Math.sqrt(1.0D - h * h)) % (Math.PI * 2.0D);
        } else {
            latitude = 0.0D;
            longitude = 0.0D;
        }
        ray++;
    }

    private static float masqueradeResistance(BlockState state) {
        if (state.is(Blocks.SANDSTONE)) return Blocks.STONE.getExplosionResistance();
        if (state.is(Blocks.OBSIDIAN)) return Blocks.STONE.getExplosionResistance() * 3.0F;
        return state.getBlock().getExplosionResistance();
    }

    public void destructionTick(int milliseconds) {
        if (!cacheComplete || milliseconds <= 0) return;
        long deadline = System.nanoTime() + milliseconds * 1_000_000L;
        while (nextChunk < orderedChunks.size() && System.nanoTime() < deadline) {
            if (activeTips == null) beginChunk();
            if (activeRemovalIterator == null) {
                if (activeTipIndex < activeTips.size()) {
                    collectTipBlocks(activeTips.get(activeTipIndex++));
                    continue;
                }
                activeRemovalIterator = activeRemove.iterator();
            }
            if (activeRemovalIterator.hasNext()) {
                long packed = activeRemovalIterator.nextLong();
                level.setBlock(BlockPos.of(packed), Blocks.AIR.defaultBlockState(),
                        activeTipBlocks.contains(packed) ? 3 : 2);
            } else {
                finishChunk();
            }
        }
        if (nextChunk >= orderedChunks.size() && System.nanoTime() < deadline) {
            cleanupFluids(deadline);
        }
    }

    private void beginChunk() {
        activeChunkKey = orderedChunks.get(nextChunk);
        activeTips = perChunk.get(activeChunkKey);
        activeTipIndex = 0;
        activeChunkX = ChunkPos.getX(activeChunkKey);
        activeChunkZ = ChunkPos.getZ(activeChunkKey);
        activeEnter = Math.max(0, Math.min(Math.abs(centerX - (activeChunkX << 4)),
                Math.abs(centerZ - (activeChunkZ << 4))) - 16);
        activeRemove = new LongOpenHashSet();
        activeTipBlocks = new LongOpenHashSet();
        activeRemovalIterator = null;
    }

    private void collectTipBlocks(Tip tip) {
        double vectorX = tip.x - centerX;
        double vectorY = tip.y - centerY;
        double vectorZ = tip.z - centerZ;
        double vectorLength = Math.sqrt(vectorX * vectorX + vectorY * vectorY + vectorZ * vectorZ);
        if (vectorLength == 0.0D) return;
        double directionX = vectorX / vectorLength;
        double directionY = vectorY / vectorLength;
        double directionZ = vectorZ / vectorLength;
        long tipBlock = BlockPos.asLong(Mth.floor(tip.x), Mth.floor(tip.y), Mth.floor(tip.z));
        boolean entered = false;
        for (int i = activeEnter; i < vectorLength; i++) {
            tracePos.set(Mth.floor(centerX + directionX * i), Mth.floor(centerY + directionY * i),
                    Mth.floor(centerZ + directionZ * i));
            if ((tracePos.getX() >> 4) != activeChunkX || (tracePos.getZ() >> 4) != activeChunkZ) {
                if (entered) break;
                continue;
            }
            entered = true;
            if (!level.getBlockState(tracePos).isAir()) {
                long packed = tracePos.asLong();
                activeRemove.add(packed);
                if (packed == tipBlock) activeTipBlocks.add(packed);
            }
        }
    }

    /** Rays miss sheets of water, so queue every wet chunk in the sphere. */
    private void prepareFluidCleanupChunks() {
        fluidChunks.clear();
        int radius = Math.max(0, length - 1);
        int minChunkX = (centerX - radius) >> 4;
        int maxChunkX = (centerX + radius) >> 4;
        int minChunkZ = (centerZ - radius) >> 4;
        int maxChunkZ = (centerZ + radius) >> 4;
        long radiusSquared = (long) radius * radius;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            int minX = chunkX << 4;
            int maxX = minX + 15;
            long dx = centerX < minX ? (long) minX - centerX : centerX > maxX ? (long) centerX - maxX : 0L;
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int minZ = chunkZ << 4;
                int maxZ = minZ + 15;
                long dz = centerZ < minZ ? (long) minZ - centerZ : centerZ > maxZ ? (long) centerZ - maxZ : 0L;
                if (dx * dx + dz * dz <= radiusSquared) fluidChunks.add(ChunkPos.asLong(chunkX, chunkZ));
            }
        }

        int centerChunkX = centerX >> 4;
        int centerChunkZ = centerZ >> 4;
        fluidChunks.sort(Comparator.comparingInt(key ->
                Math.abs(centerChunkX - ChunkPos.getX(key)) + Math.abs(centerChunkZ - ChunkPos.getZ(key))));
    }

    /** Ten passes, one per tick, until Minecraft stops putting the water back. */
    private void cleanupFluids(long deadline) {
        if (fluidCleanupPasses >= FLUID_SETTLE_PASSES) return;
        int radius = Math.max(0, length - 1);
        long radiusSquared = (long) radius * radius;

        while (fluidChunkIndex < fluidChunks.size()) {
            long chunkKey = fluidChunks.get(fluidChunkIndex);
            LevelChunk chunk = level.getChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            LevelChunkSection[] sections = chunk.getSections();

            while (fluidSectionIndex < sections.length) {
                LevelChunkSection section = sections[fluidSectionIndex];
                int sectionY = chunk.getSectionYFromSectionIndex(fluidSectionIndex) << 4;
                int minY = sectionY;
                int maxY = sectionY + 15;
                long dy = centerY < minY ? (long) minY - centerY : centerY > maxY ? (long) centerY - maxY : 0L;

                if (fluidBlockIndex == 0 && (dy * dy > radiusSquared
                        || !section.getStates().maybeHas(state -> !state.getFluidState().isEmpty()))) {
                    fluidSectionIndex++;
                    continue;
                }

                while (fluidBlockIndex < LevelChunkSection.SECTION_SIZE) {
                    int packed = fluidBlockIndex++;
                    int localX = packed & 15;
                    int localZ = packed >> 4 & 15;
                    int localY = packed >> 8 & 15;
                    int x = (ChunkPos.getX(chunkKey) << 4) + localX;
                    int y = sectionY + localY;
                    int z = (ChunkPos.getZ(chunkKey) << 4) + localZ;
                    long offsetX = (long) x - centerX;
                    long offsetY = (long) y - centerY;
                    long offsetZ = (long) z - centerZ;

                    if (offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ <= radiusSquared
                            && !section.getFluidState(localX, localY, localZ).isEmpty()) {
                        tracePos.set(x, y, z);
                        level.setBlock(tracePos, Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                    }
                    if ((fluidBlockIndex & 255) == 0 && System.nanoTime() >= deadline) return;
                }

                fluidBlockIndex = 0;
                fluidSectionIndex++;
            }

            fluidSectionIndex = 0;
            fluidChunkIndex++;
            if (System.nanoTime() >= deadline) return;
        }

        fluidChunkIndex = 0;
        fluidSectionIndex = 0;
        fluidBlockIndex = 0;
        fluidCleanupPasses++;
    }

    private void finishChunk() {
        perChunk.remove(activeChunkKey);
        nextChunk++;
        activeTips = null;
        activeRemove = null;
        activeTipBlocks = null;
        activeRemovalIterator = null;
    }

    public boolean isComplete() {
        return cacheComplete && perChunk.isEmpty()
                && fluidCleanupPasses >= FLUID_SETTLE_PASSES;
    }
    public int cachedChunkCount() { return perChunk.size(); }
    public int generatedRays() { return Math.min(ray - 1, rayCount); }
    public int totalRays() { return rayCount; }
    public void cancel() {
        perChunk.clear();
        orderedChunks.clear();
        activeTips = null;
        activeRemove = null;
        activeTipBlocks = null;
        activeRemovalIterator = null;
        fluidChunks.clear();
        fluidChunkIndex = 0;
        fluidSectionIndex = 0;
        fluidBlockIndex = 0;
        fluidCleanupPasses = FLUID_SETTLE_PASSES;
        cacheComplete = true;
    }

    private record Tip(float x, float y, float z) { }
}
