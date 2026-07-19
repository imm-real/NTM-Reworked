package com.hbm.ntm.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/** Shared vein and depth-deposit geometry from the old world generator. */
public final class LegacyOreGeneration {
    private LegacyOreGeneration() { }

    public static int veins(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, int count,
                             int size, int minAboveBottom, int range, BlockState ore,
                             Predicate<BlockState> target) {
        int changed = 0;
        for (int i = 0; i < count; i++) {
            int x = chunkX + random.nextInt(16);
            int y = LegacyWorldgenHeights.aboveBottom(level, minAboveBottom + (range > 0 ? random.nextInt(range) : 0));
            int z = chunkZ + random.nextInt(16);
            changed += vein(level, random, new BlockPos(x, y, z), size, ore, target);
        }
        return changed;
    }

    /** Geometry from 1.7.10 WorldGenMinable. */
    public static int vein(WorldGenLevel level, RandomSource random, BlockPos origin, int size,
                           BlockState ore, Predicate<BlockState> target) {
        float angle = random.nextFloat() * Mth.PI;
        double x0 = origin.getX() + 8 + Mth.sin(angle) * size / 8.0F;
        double x1 = origin.getX() + 8 - Mth.sin(angle) * size / 8.0F;
        double z0 = origin.getZ() + 8 + Mth.cos(angle) * size / 8.0F;
        double z1 = origin.getZ() + 8 - Mth.cos(angle) * size / 8.0F;
        double y0 = origin.getY() + random.nextInt(3) - 2;
        double y1 = origin.getY() + random.nextInt(3) - 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;

        for (int step = 0; step <= size; step++) {
            double cx = x0 + (x1 - x0) * step / size;
            double cy = y0 + (y1 - y0) * step / size;
            double cz = z0 + (z1 - z0) * step / size;
            double randomScale = random.nextDouble() * size / 16.0D;
            double diameterXZ = (Mth.sin(Mth.PI * step / size) + 1.0F) * randomScale + 1.0D;
            double diameterY = (Mth.sin(Mth.PI * step / size) + 1.0F) * randomScale + 1.0D;
            int minX = Mth.floor(cx - diameterXZ / 2.0D);
            int minY = Mth.floor(cy - diameterY / 2.0D);
            int minZ = Mth.floor(cz - diameterXZ / 2.0D);
            int maxX = Mth.floor(cx + diameterXZ / 2.0D);
            int maxY = Mth.floor(cy + diameterY / 2.0D);
            int maxZ = Mth.floor(cz + diameterXZ / 2.0D);
            for (int x = minX; x <= maxX; x++) {
                double dx = (x + 0.5D - cx) / (diameterXZ / 2.0D);
                if (dx * dx >= 1.0D) continue;
                for (int y = Math.max(minY, level.getMinBuildHeight());
                     y <= Math.min(maxY, level.getMaxBuildHeight() - 1); y++) {
                    double dy = (y + 0.5D - cy) / (diameterY / 2.0D);
                    if (dx * dx + dy * dy >= 1.0D) continue;
                    for (int z = minZ; z <= maxZ; z++) {
                        double dz = (z + 0.5D - cz) / (diameterXZ / 2.0D);
                        if (dx * dx + dy * dy + dz * dz >= 1.0D) continue;
                        cursor.set(x, y, z);
                        if (target.test(level.getBlockState(cursor))) {
                            level.setBlock(cursor, ore, Block.UPDATE_CLIENTS);
                            changed++;
                        }
                    }
                }
            }
        }
        return changed;
    }

    public static int depthDeposit(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ,
                                   int size, double fill, int chance, BlockState ore, BlockState filler,
                                   Predicate<BlockState> target, int centerMin, int centerRange) {
        if (random.nextInt(chance) != 0) return 0;
        int centerX = chunkX + random.nextInt(16) + 8;
        int centerY = LegacyWorldgenHeights.aboveBottom(level, centerMin + random.nextInt(centerRange));
        int centerZ = chunkZ + random.nextInt(16) + 8;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;
        int minY = LegacyWorldgenHeights.aboveBottom(level, 1);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, LegacyWorldgenHeights.aboveBottom(level, 126));
        for (int x = centerX - size; x <= centerX + size; x++) {
            for (int y = Math.max(centerY - size, minY); y <= Math.min(centerY + size, maxY); y++) {
                for (int z = centerZ - size; z <= centerZ + size; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!target.test(state) && !state.is(Blocks.BEDROCK)) continue;
                    double distance = Math.sqrt(cursor.distSqr(new BlockPos(centerX, centerY, centerZ)));
                    if (distance + random.nextInt(2) < size * fill) {
                        level.setBlock(cursor, ore, Block.UPDATE_CLIENTS);
                        changed++;
                    } else if (distance + random.nextInt(2) <= size) {
                        level.setBlock(cursor, filler, Block.UPDATE_CLIENTS);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    public static boolean stone(BlockState state) {
        return state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    }

    public static int bedrockNode(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ,
                                  BlockState ore, BlockState filler, Predicate<BlockState> target) {
        int centerX = chunkX + random.nextInt(2) + 8;
        int centerZ = chunkZ + random.nextInt(2) + 8;
        int bottom = level.getMinBuildHeight();
        int changed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = centerX - 1; x <= centerX + 1; x++) for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            cursor.set(x, bottom, z);
            if (level.getBlockState(cursor).is(Blocks.BEDROCK)
                    && ((x == centerX && z == centerZ) || random.nextBoolean())) {
                level.setBlock(cursor, ore, Block.UPDATE_CLIENTS);
                changed++;
            }
        }
        for (int x = centerX - 3; x <= centerX + 3; x++) for (int z = centerZ - 3; z <= centerZ + 3; z++) {
            for (int above = 1; above < 7; above++) {
                cursor.set(x, bottom + above, z);
                BlockState state = level.getBlockState(cursor);
                if ((above < 3 || state.is(Blocks.BEDROCK)) && (target.test(state) || state.is(Blocks.BEDROCK))) {
                    level.setBlock(cursor, filler, Block.UPDATE_CLIENTS);
                    changed++;
                }
            }
        }
        return changed;
    }
}
