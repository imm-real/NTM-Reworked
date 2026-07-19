package com.hbm.ntm.worldgen;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.function.Predicate;

/** The complete 1.7.10 Nether ore pass, including both depth-neodymium bands. */
public final class LegacyNetherOreFeature extends Feature<NoneFeatureConfiguration> {
    public LegacyNetherOreFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!level.getLevel().dimension().equals(Level.NETHER) || !HbmConfig.NETHER_ORES.get()) return false;
        RandomSource random = context.random();
        int x = context.origin().getX();
        int z = context.origin().getZ();
        Predicate<BlockState> netherrack = state -> state.is(Blocks.NETHERRACK);
        int changed = 0;
        changed += veins(level, random, x, z, 8, 6, 0, 127, "ore_nether_uranium", netherrack);
        changed += veins(level, random, x, z, 10, 10, 0, 127, "ore_nether_tungsten", netherrack);
        changed += veins(level, random, x, z, 26, 12, 0, 127, "ore_nether_sulfur", netherrack);
        changed += veins(level, random, x, z, 24, 6, 0, 127, "ore_nether_fire", netherrack);
        changed += veins(level, random, x, z, 8, 32, 16, 96, "ore_nether_coal", netherrack);
        changed += veins(level, random, x, z, 2, 6, 100, 26, "ore_nether_cobalt", netherrack);
        if (HbmConfig.PLUTONIUM_NETHER_ORE.get()) {
            changed += veins(level, random, x, z, 8, 4, 0, 127, "ore_nether_plutonium", netherrack);
        }
        changed += LegacyOreGeneration.depthDeposit(level, random, x, z, 7, 0.6D, 16,
                block("ore_depth_nether_neodymium"), block("stone_depth_nether"), netherrack, 0, 3);
        changed += LegacyOreGeneration.depthDeposit(level, random, x, z, 7, 0.6D, 16,
                block("ore_depth_nether_neodymium"), block("stone_depth_nether"), netherrack, 125, 3);
        if (random.nextInt(10) == 0) {
            changed += LegacyOreGeneration.bedrockNode(level, random, x, z, block("ore_bedrock"),
                    block("stone_depth_nether"), netherrack);
        }
        return changed > 0;
    }

    private static int veins(WorldGenLevel level, RandomSource random, int x, int z, int count, int size,
                             int min, int range, String id, Predicate<BlockState> target) {
        return LegacyOreGeneration.veins(level, random, x, z, count, size, min, range, block(id), target);
    }
    private static BlockState block(String id) { return ModBlocks.legacy(id).get().defaultBlockState(); }
}
