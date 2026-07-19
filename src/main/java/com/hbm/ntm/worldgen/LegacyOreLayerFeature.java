package com.hbm.ntm.worldgen;

import com.hbm.ntm.block.StoneResourceBlock;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** OreLayer3D, now aware that the world continues below zero. */
public final class LegacyOreLayerFeature extends Feature<NoneFeatureConfiguration> {
    public static final int MIN_Y_ABOVE_BOTTOM = 6;
    public static final int MAX_Y_ABOVE_BOTTOM = 64;

    private final StoneResourceBlock.Type resourceType;
    private final int sourceLayerId;
    private final double horizontalScale;
    private final double verticalScale;
    private final double threshold;
    private final Map<Long, NoisePair> noiseBySeed = new ConcurrentHashMap<>();

    public LegacyOreLayerFeature(StoneResourceBlock.Type resourceType, int sourceLayerId,
                                 double horizontalScale, double verticalScale, double threshold) {
        super(NoneFeatureConfiguration.CODEC);
        this.resourceType = resourceType;
        this.sourceLayerId = sourceLayerId;
        this.horizontalScale = horizontalScale;
        this.verticalScale = verticalScale;
        this.threshold = threshold;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!level.getLevel().dimension().equals(Level.OVERWORLD) || !enabled()) return false;

        long seed = level.getLevel().getSeed();
        NoisePair noise = noiseBySeed.computeIfAbsent(seed, this::createNoise);
        BlockPos origin = context.origin();
        double[][] cacheX = new double[16][MAX_Y_ABOVE_BOTTOM + 1];

        // Shifted sixteen-by-sixteen sampling, for historical reasons.
        for (int offset = 0; offset < 16; offset++) {
            for (int legacyY = MAX_Y_ABOVE_BOTTOM; legacyY >= MIN_Y_ABOVE_BOTTOM; legacyY--) {
                cacheX[offset][legacyY] = noise.x().value(
                        legacyY * verticalScale,
                        (origin.getZ() + 8 + offset) * horizontalScale);
            }
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (int offsetX = 0; offsetX < 16; offsetX++) {
            int x = origin.getX() + 8 + offsetX;
            for (int offsetZ = 0; offsetZ < 16; offsetZ++) {
                int z = origin.getZ() + 8 + offsetZ;
                double ny = noise.y().value(x * horizontalScale, z * horizontalScale);

                for (int legacyY = MAX_Y_ABOVE_BOTTOM; legacyY >= MIN_Y_ABOVE_BOTTOM; legacyY--) {
                    // Yes, cacheX twice. cacheZ is decorative storage.
                    double nx = cacheX[offsetZ][legacyY];
                    double nz = cacheX[offsetX][legacyY];
                    if (nx * ny * nz <= threshold) continue;

                    int y = LegacyWorldgenHeights.aboveBottom(level, legacyY);
                    if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
                    cursor.set(x, y, z);
                    BlockState target = level.getBlockState(cursor);
                    if (!isOreReplaceable(target)) continue;

                    level.setBlock(cursor, ModBlocks.STONE_RESOURCE.get().defaultBlockState()
                            .setValue(StoneResourceBlock.TYPE, resourceType), Block.UPDATE_CLIENTS);
                    changed++;
                }
            }
        }
        return changed > 0;
    }

    private boolean enabled() {
        return switch (resourceType) {
            case HEMATITE -> HbmConfig.HEMATITE_DEPOSITS.get();
            case MALACHITE -> HbmConfig.MALACHITE_DEPOSITS.get();
            case BAUXITE -> HbmConfig.BAUXITE_DEPOSITS.get();
            default -> true;
        };
    }

    private NoisePair createNoise(long seed) {
        return new NoisePair(
                new LegacyOctaveNoise2D(seed + 101L + sourceLayerId, 4),
                new LegacyOctaveNoise2D(seed + 102L + sourceLayerId, 4));
    }

    private static boolean isOreReplaceable(BlockState state) {
        return state.is(BlockTags.STONE_ORE_REPLACEABLES)
                || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    }

    public StoneResourceBlock.Type resourceType() {
        return resourceType;
    }

    public int sourceLayerId() {
        return sourceLayerId;
    }

    public double horizontalScale() {
        return horizontalScale;
    }

    public double verticalScale() {
        return verticalScale;
    }

    public double threshold() {
        return threshold;
    }

    private record NoisePair(LegacyOctaveNoise2D x, LegacyOctaveNoise2D y) {
    }
}
