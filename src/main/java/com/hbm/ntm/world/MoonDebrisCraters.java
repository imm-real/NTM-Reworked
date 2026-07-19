package com.hbm.ntm.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Short-lived server authority and terrain carving for the moon rupture's local debris showers. */
public final class MoonDebrisCraters {
    private static final long FIRST_IMPACT_DELAY = 100L;
    private static final long IMPACT_WINDOW = 760L;
    private static final int MAX_IMPACTS_PER_PLAYER = 64;
    private static final int MAX_IMPACTS_PER_TICK = 5;
    private static final double MAX_HORIZONTAL_DISTANCE = 192.0D;

    private static WeakReference<MinecraftServer> activeServer = new WeakReference<>(null);
    private static long windowStart = Long.MAX_VALUE;
    private static long windowEnd = Long.MIN_VALUE;
    private static final Map<UUID, ImpactBudget> BUDGETS = new HashMap<>();
    private static final Set<Long> USED_COLUMNS = new HashSet<>();

    private MoonDebrisCraters() {
    }

    public static void begin(ServerLevel overworld) {
        activeServer = new WeakReference<>(overworld.getServer());
        long now = overworld.getGameTime();
        windowStart = now + FIRST_IMPACT_DELAY;
        windowEnd = now + IMPACT_WINDOW;
        BUDGETS.clear();
        USED_COLUMNS.clear();
    }

    public static void tryCreate(ServerPlayer player, int x, int z, int sizeTenths) {
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        long now = level.getGameTime();
        if (level.dimension() != Level.OVERWORLD || activeServer.get() != server
                || now < windowStart || now > windowEnd
                || !MoonDestructionData.get(level).isDestroyed()) return;

        double deltaX = x + 0.5D - player.getX();
        double deltaZ = z + 0.5D - player.getZ();
        if (deltaX * deltaX + deltaZ * deltaZ > MAX_HORIZONTAL_DISTANCE * MAX_HORIZONTAL_DISTANCE
                || !level.hasChunk(x >> 4, z >> 4)) return;

        ImpactBudget budget = BUDGETS.computeIfAbsent(player.getUUID(), ignored -> new ImpactBudget());
        if (!budget.consume(now)) return;
        long column = BlockPos.asLong(x, 0, z);
        if (!USED_COLUMNS.add(column)) return;

        float size = Mth.clamp(sizeTenths, 8, 80) / 10.0F;
        carve(level, x, z, size, player.getUUID().getLeastSignificantBits() ^ now);
    }

    static void carve(ServerLevel level, int x, int z, float size, long salt) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (surface < level.getMinBuildHeight() || surface >= level.getMaxBuildHeight()) return;

        float radius = radiusForSize(size);
        float depth = depthForSize(size);
        int bounds = Mth.ceil(radius + 1.0F);
        RandomSource random = RandomSource.create(BlockPos.asLong(x, surface, z) ^ salt);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int deltaX = -bounds; deltaX <= bounds; deltaX++) {
            for (int deltaZ = -bounds; deltaZ <= bounds; deltaZ++) {
                float angleNoise = 0.80F + random.nextFloat() * 0.34F;
                double horizontal = (deltaX * deltaX + deltaZ * deltaZ) / (radius * radius * angleNoise);
                if (horizontal > 1.0D) continue;
                int columnDepth = Math.max(1, Mth.ceil((1.0D - horizontal) * depth));
                int columnTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        x + deltaX, z + deltaZ) - 1;
                int bottom = Math.max(level.getMinBuildHeight(), columnTop - columnDepth + 1);
                for (int y = columnTop; y >= bottom; y--) {
                    cursor.set(x + deltaX, y, z + deltaZ);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.getDestroySpeed(level, cursor) < 0.0F) continue;
                    BlockEntity blockEntity = level.getBlockEntity(cursor);
                    if (blockEntity != null) continue;
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    static float radiusForSize(float size) {
        return Mth.clamp(1.35F + size * 0.50F, 1.75F, 5.25F);
    }

    static float depthForSize(float size) {
        return Mth.clamp(1.0F + size * 0.30F, 1.25F, 3.4F);
    }

    private static final class ImpactBudget {
        private int total;
        private long lastTick = Long.MIN_VALUE;
        private int thisTick;

        private boolean consume(long gameTime) {
            if (total >= MAX_IMPACTS_PER_PLAYER) return false;
            if (lastTick != gameTime) {
                lastTick = gameTime;
                thisTick = 0;
            }
            if (thisTick >= MAX_IMPACTS_PER_TICK) return false;
            thisTick++;
            total++;
            return true;
        }
    }
}
