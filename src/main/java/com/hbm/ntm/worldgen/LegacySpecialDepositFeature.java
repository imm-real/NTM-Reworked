package com.hbm.ntm.worldgen;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Bedrock oil nodes and hot-desert fuzzy oil-sand bubbles from the chainloader pass. */
public final class LegacySpecialDepositFeature extends Feature<NoneFeatureConfiguration> {
    public LegacySpecialDepositFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!level.getLevel().dimension().equals(Level.OVERWORLD)) return false;
        int changed = 0;
        int bedrockRate = HbmConfig.BEDROCK_OIL_RATE.get();
        if (bedrockRate > 0 && context.random().nextInt(bedrockRate) == bedrockRate - 2) {
            changed += bedrockOil(level, context.random(), context.origin());
        }
        int sandRate = HbmConfig.OIL_SAND_RATE.get();
        Biome biome = level.getBiome(context.origin()).value();
        if (sandRate > 0 && biome.getBaseTemperature() >= 1.5F
                && biome.getModifiedClimateSettings().downfall() < 0.1F
                && context.random().nextInt(Math.max(1, sandRate / 3)) == Math.max(1, sandRate / 3) - 1) {
            changed += oilSand(level, context.random(), context.origin());
        }
        return changed > 0;
    }

    private static int bedrockOil(WorldGenLevel level, RandomSource random, BlockPos origin) {
        int centerX = origin.getX() + random.nextInt(16);
        int centerZ = origin.getZ() + random.nextInt(16);
        int bottom = level.getMinBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (int x = centerX - 6; x <= centerX + 6; x++) for (int z = centerZ - 6; z <= centerZ + 6; z++) {
            for (int above = 0; above < 5; above++) {
                if (Math.abs(x - centerX) >= 5 || Math.abs(z - centerZ) >= 5
                        || Math.abs(x - centerX) + above + Math.abs(z - centerZ) > 6) continue;
                cursor.set(x, bottom + above, z);
                BlockState state = level.getBlockState(cursor);
                if (LegacyOreGeneration.stone(state) || state.is(Blocks.BEDROCK)) {
                    level.setBlock(cursor, ModBlocks.legacy("ore_bedrock_oil").get().defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                    changed++;
                }
            }
        }
        scar(level, random, centerX, centerZ, 50, 5);
        return changed;
    }

    private static int oilSand(WorldGenLevel level, RandomSource random, BlockPos origin) {
        int centerX = origin.getX() + random.nextInt(16);
        int centerZ = origin.getZ() + random.nextInt(16);
        int centerY = LegacyWorldgenHeights.aboveBottom(level, 56 + random.nextInt(16));
        int radius = 16 + random.nextInt(32);
        double radiusSquared = radius * radius / 2.0D;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int vertical = Math.max(1, radius / 2);
                for (int y = Math.max(level.getMinBuildHeight(), centerY - vertical);
                     y <= Math.min(level.getMaxBuildHeight() - 1, centerY + vertical); y++) {
                    int dx = x - centerX, dz = z - centerZ, dy = centerY - y;
                    double distance = dx * dx + dz * dz + dy * dy * 3.0D
                            - random.nextDouble() * radiusSquared / 3.0D;
                    if (distance >= radiusSquared) continue;
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) {
                        level.setBlock(cursor, ModBlocks.legacy("ore_oil_sand").get().defaultBlockState(),
                                Block.UPDATE_CLIENTS);
                        changed++;
                    }
                }
            }
        }
        scar(level, random, centerX, centerZ, 150, 7);
        return changed;
    }

    private static void scar(WorldGenLevel level, RandomSource random, int centerX, int centerZ,
                             int count, int width) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            int x = centerX + (int) (random.nextGaussian() * width);
            int z = centerZ + (int) (random.nextGaussian() * width);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
                level.setBlock(cursor, (random.nextInt(10) == 0 ? ModBlocks.DIRT_OILY : ModBlocks.DIRT_DEAD)
                        .get().defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (state.is(Blocks.SAND)) {
                level.setBlock(cursor, ModBlocks.SAND_DIRTY.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (state.is(Blocks.RED_SAND)) {
                level.setBlock(cursor, ModBlocks.SAND_DIRTY_RED.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
