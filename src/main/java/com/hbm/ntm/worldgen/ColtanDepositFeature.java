package com.hbm.ntm.worldgen;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Random;

/** Source seed-anchored, density-graded Coltan deposit used by normal progression. */
public final class ColtanDepositFeature extends Feature<NoneFeatureConfiguration> {
    public static final int DEPOSIT_RANGE = 750;
    public static final int VEIN_SIZE = 4;
    public static final int ATTEMPTS_PER_CHUNK = 2;
    public static final int DENSITY_BANDS = 5;
    public static final int MIN_Y = 15;
    public static final int Y_RANGE = 25;

    public ColtanDepositFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!HbmConfig.OVERWORLD_ORES.get() || !HbmConfig.COLTAN_DEPOSIT.get()
                || !level.getLevel().dimension().equals(Level.OVERWORLD)) return false;

        BlockPos center = depositCenter(level.getLevel().getSeed());
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        int changed = 0;

        for (int attempt = 0; attempt < ATTEMPTS_PER_CHUNK; attempt++) {
            for (int band = 1; band <= DENSITY_BANDS; band++) {
                int x = origin.getX() + random.nextInt(16);
                int y = LegacyWorldgenHeights.aboveBottom(level, MIN_Y + random.nextInt(Y_RANGE));
                int z = origin.getZ() + random.nextInt(16);
                if (insideBand(center, x, z, band)) {
                    changed += generateVein(level, random, x, y, z, VEIN_SIZE);
                }
            }
        }
        return changed > 0;
    }

    public static BlockPos depositCenter(long worldSeed) {
        Random random = new Random(worldSeed + 5L);
        return new BlockPos((int) (random.nextGaussian() * 1_500D), 0,
                (int) (random.nextGaussian() * 1_500D));
    }

    public static boolean insideBand(BlockPos center, int x, int z, int band) {
        if (band < 1 || band > DENSITY_BANDS) return false;
        int range = DEPOSIT_RANGE / band;
        return x >= center.getX() - range && x <= center.getX() + range
                && z >= center.getZ() - range && z <= center.getZ() + range;
    }

    /** Places the four-block vein used by the old generator. */
    private static int generateVein(WorldGenLevel level, RandomSource random,
                                    int x, int y, int z, int size) {
        float angle = random.nextFloat() * Mth.PI;
        double startX = x + 8D + Mth.sin(angle) * size / 8D;
        double endX = x + 8D - Mth.sin(angle) * size / 8D;
        double startZ = z + 8D + Mth.cos(angle) * size / 8D;
        double endZ = z + 8D - Mth.cos(angle) * size / 8D;
        double startY = y + random.nextInt(3) - 2;
        double endY = y + random.nextInt(3) - 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;

        for (int step = 0; step <= size; step++) {
            double centerX = Mth.lerp((double) step / size, startX, endX);
            double centerY = Mth.lerp((double) step / size, startY, endY);
            double centerZ = Mth.lerp((double) step / size, startZ, endZ);
            double randomRadius = random.nextDouble() * size / 16D;
            double diameter = (Mth.sin(step * Mth.PI / size) + 1D) * randomRadius + 1D;
            double horizontalRadius = diameter / 2D;
            double verticalRadius = diameter / 2D;

            int minX = Mth.floor(centerX - horizontalRadius);
            int maxX = Mth.floor(centerX + horizontalRadius);
            int minY = Math.max(level.getMinBuildHeight(), Mth.floor(centerY - verticalRadius));
            int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(centerY + verticalRadius));
            int minZ = Mth.floor(centerZ - horizontalRadius);
            int maxZ = Mth.floor(centerZ + horizontalRadius);

            for (int blockX = minX; blockX <= maxX; blockX++) {
                double dx = (blockX + 0.5D - centerX) / horizontalRadius;
                if (dx * dx >= 1D) continue;
                for (int blockY = minY; blockY <= maxY; blockY++) {
                    double dy = (blockY + 0.5D - centerY) / verticalRadius;
                    if (dx * dx + dy * dy >= 1D) continue;
                    for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                        double dz = (blockZ + 0.5D - centerZ) / horizontalRadius;
                        if (dx * dx + dy * dy + dz * dz >= 1D) continue;
                        cursor.set(blockX, blockY, blockZ);
                        BlockState existing = level.getBlockState(cursor);
                        if (existing.is(Blocks.STONE) || existing.is(Blocks.DEEPSLATE)) {
                            level.setBlock(cursor, ModBlocks.ORE_COLTAN.get().defaultBlockState(),
                                    Block.UPDATE_CLIENTS);
                            changed++;
                        }
                    }
                }
            }
        }
        return changed;
    }
}
