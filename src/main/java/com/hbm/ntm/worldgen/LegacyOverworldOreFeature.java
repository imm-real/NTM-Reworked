package com.hbm.ntm.worldgen;

import com.hbm.ntm.block.StoneResourceBlock;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Overworld veins, clusters, Gneiss, caves and the unpleasant depths below. */
public final class LegacyOverworldOreFeature extends Feature<NoneFeatureConfiguration> {
    private final Map<Long, LegacyOctaveNoise2D> gneissNoise = new ConcurrentHashMap<>();
    private final Map<Long, LegacyOctaveNoise2D> sulfurNoise = new ConcurrentHashMap<>();
    private final Map<Long, LegacyOctaveNoise2D> asbestosNoise = new ConcurrentHashMap<>();

    public LegacyOverworldOreFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!level.getLevel().dimension().equals(Level.OVERWORLD)) return false;
        RandomSource random = context.random();
        int x = context.origin().getX();
        int z = context.origin().getZ();
        int changed = 0;

        changed += gasBubble(level, random, x, z, HbmConfig.GAS_BUBBLE_RATE.get(), "gas_flammable");
        changed += gasBubble(level, random, x, z, HbmConfig.EXPLOSIVE_GAS_BUBBLE_RATE.get(), "gas_explosive");
        int alexRate = HbmConfig.ALEXANDRITE_RATE.get();
        if (alexRate > 0 && random.nextInt(alexRate) == 0) {
            changed += LegacyOreGeneration.veins(level, random, x, z, 1, 3, 10, 5,
                    block("ore_alexandrite"), LegacyOreGeneration::stone);
        }
        if (!HbmConfig.OVERWORLD_ORES.get()) return changed > 0;

        changed += generateGneissStratum(level, x, z);
        changed += caveStratum(level, random, x, z, true);
        changed += caveStratum(level, random, x, z, false);
        changed += depth(level, random, x, z, "cluster_depth_iron", 0.6D, 24);
        changed += depth(level, random, x, z, "cluster_depth_titanium", 0.6D, 32);
        changed += depth(level, random, x, z, "cluster_depth_tungsten", 0.6D, 32);
        changed += depth(level, random, x, z, "ore_depth_cinnebar", 0.8D, 16);
        changed += depth(level, random, x, z, "ore_depth_zirconium", 0.8D, 16);
        changed += depth(level, random, x, z, "ore_depth_borax", 0.8D, 16);

        Predicate<BlockState> gneiss = state -> state.is(ModBlocks.legacy("stone_gneiss").get());
        changed += veins(level, random, x, z, 25, 6, 30, 10, "ore_gneiss_iron", gneiss);
        changed += veins(level, random, x, z, 10, 6, 30, 10, "ore_gneiss_gold", gneiss);
        changed += veins(level, random, x, z, 21, 6, 30, 10, "ore_gneiss_uranium", gneiss);
        changed += veins(level, random, x, z, 36, 6, 30, 10, "ore_gneiss_copper", gneiss);
        changed += veins(level, random, x, z, 6, 6, 30, 10, "ore_gneiss_asbestos", gneiss);
        changed += veins(level, random, x, z, 6, 6, 30, 10, "ore_gneiss_lithium", gneiss);
        changed += veins(level, random, x, z, HbmConfig.RARE_EARTH_SPAWN_RATE.get(), 6, 30, 10,
                "ore_gneiss_rare", gneiss);
        changed += veins(level, random, x, z, 15, 10, 30, 10, "ore_gneiss_gas", gneiss);

        changed += veins(level, random, x, z, 7, 5, 5, 20, "ore_uranium", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 7, 5, 5, 25, "ore_thorium", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, HbmConfig.TITANIUM_SPAWN_RATE.get(), 6, 5, 30,
                ModBlocks.ORE_TITANIUM.get().defaultBlockState(), LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 5, 8, 5, 30, "ore_sulfur", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 7, 6, 5, 40, "ore_aluminium", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 12, 6, 5, 45, "ore_copper", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 6, 4, 5, 45, "ore_fluorite", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 6, 6, 5, 30, "ore_niter", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, HbmConfig.TUNGSTEN_SPAWN_RATE.get(), 8, 5, 30,
                ModBlocks.ORE_TUNGSTEN.get().defaultBlockState(), LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 6, 9, 5, 30, "ore_lead", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 6, 4, 5, 30, "ore_beryllium", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, HbmConfig.RARE_EARTH_SPAWN_RATE.get(), 5, 5, 20,
                ModBlocks.ORE_RARE.get().defaultBlockState(), LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 2, 24, 35, 25, "ore_lignite", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 2, 4, 16, 16, "ore_asbestos", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 1, 4, 8, 16, "ore_cinnebar", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, HbmConfig.COBALT_SPAWN_RATE.get(), 4, 4, 8,
                ModBlocks.ORE_COBALT.get().defaultBlockState(), LegacyOreGeneration::stone);

        changed += veins(level, random, x, z, 4, 6, 15, 45, "cluster_iron", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 2, 6, 15, 30, "cluster_titanium", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 3, 6, 15, 35, "cluster_aluminium", LegacyOreGeneration::stone);
        changed += veins(level, random, x, z, 4, 6, 15, 20, "cluster_copper", LegacyOreGeneration::stone);
        changed += LegacyOreGeneration.veins(level, random, x, z, 1, 16, 25, 30,
                ModBlocks.STONE_RESOURCE.get().defaultBlockState().setValue(StoneResourceBlock.TYPE,
                        StoneResourceBlock.Type.LIMESTONE), LegacyOreGeneration::stone);

        if (HbmConfig.REGULAR_COLTAN_ORE.get()) {
            changed += LegacyOreGeneration.veins(level, random, x, z, 2, 4, 15, 40,
                    ModBlocks.ORE_COLTAN.get().defaultBlockState(), LegacyOreGeneration::stone);
        }
        if (random.nextInt(10) == 0) changed += bedrockOre(level, random, x, z);
        return changed > 0;
    }

    private int gasBubble(WorldGenLevel level, RandomSource random, int x, int z, int rate, String id) {
        if (rate <= 0 || random.nextInt(rate) != 0) return 0;
        return veins(level, random, x, z, 1, 32, 30, 10, id, LegacyOreGeneration::stone);
    }

    private int depth(WorldGenLevel level, RandomSource random, int x, int z, String id, double fill, int chance) {
        return LegacyOreGeneration.depthDeposit(level, random, x, z, 5, fill, chance, block(id),
                block("stone_depth"), LegacyOreGeneration::stone, 0, 3);
    }

    private int generateGneissStratum(WorldGenLevel level, int originX, int originZ) {
        LegacyOctaveNoise2D noise = gneissNoise.computeIfAbsent(level.getLevel().getSeed(),
                seed -> new LegacyOctaveNoise2D(seed, 4));
        int changed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = originX + 8; x < originX + 24; x++) for (int z = originZ + 8; z < originZ + 24; z++) {
            double n = noise.value(x * 0.01D, z * 0.01D);
            if (n <= 5.0D) continue;
            int range = (int) ((n - 5.0D) * 3.0D);
            if (range > 4) range = 8 - range;
            if (range < 0) continue;
            for (int legacyY = 30 - range; legacyY <= 30 + range; legacyY++) {
                cursor.set(x, LegacyWorldgenHeights.aboveBottom(level, legacyY), z);
                if (LegacyOreGeneration.stone(level.getBlockState(cursor))) {
                    level.setBlock(cursor, block("stone_gneiss"), Block.UPDATE_CLIENTS);
                    changed++;
                }
            }
        }
        return changed;
    }

    private int caveStratum(WorldGenLevel level, RandomSource random, int originX, int originZ, boolean sulfur) {
        if (sulfur ? !HbmConfig.SULFUR_CAVES.get() : !HbmConfig.ASBESTOS_CAVES.get()) return 0;
        long seed = level.getLevel().getSeed() + (sulfur ? 30L : 25L);
        LegacyOctaveNoise2D noise = (sulfur ? sulfurNoise : asbestosNoise).computeIfAbsent(seed,
                value -> new LegacyOctaveNoise2D(value, 2));
        double threshold = sulfur ? 1.5D : 1.75D;
        int center = sulfur ? 30 : 25;
        StoneResourceBlock.Type type = sulfur ? StoneResourceBlock.Type.SULFUR : StoneResourceBlock.Type.ASBESTOS;
        BlockState ore = ModBlocks.STONE_RESOURCE.get().defaultBlockState().setValue(StoneResourceBlock.TYPE, type);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (int x = originX + 8; x < originX + 24; x++) for (int z = originZ + 8; z < originZ + 24; z++) {
            double n = noise.value(x * 0.01D, z * 0.01D);
            if (n <= threshold) continue;
            int range = (int) ((n - threshold) * 20.0D);
            if (range > 20) range = 40 - range;
            if (range < 0) continue;
            for (int legacyY = center - range; legacyY <= center + range; legacyY++) {
                cursor.set(x, LegacyWorldgenHeights.aboveBottom(level, legacyY), z);
                if (!LegacyOreGeneration.stone(level.getBlockState(cursor))) continue;
                boolean exposed = false;
                for (Direction direction : Direction.values()) {
                    BlockState neighbor = level.getBlockState(cursor.relative(direction));
                    if (neighbor.isAir() || !neighbor.isCollisionShapeFullBlock(level, cursor.relative(direction))) {
                        exposed = true;
                        break;
                    }
                }
                if (exposed) {
                    level.setBlock(cursor, ore, Block.UPDATE_CLIENTS);
                    changed++;
                }
            }
        }
        return changed;
    }

    private int bedrockOre(WorldGenLevel level, RandomSource random, int originX, int originZ) {
        int centerX = originX + random.nextInt(2) + 8;
        int centerZ = originZ + random.nextInt(2) + 8;
        int bottom = level.getMinBuildHeight();
        int changed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = centerX - 1; x <= centerX + 1; x++) for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            cursor.set(x, bottom, z);
            if (level.getBlockState(cursor).is(Blocks.BEDROCK)
                    && ((x == centerX && z == centerZ) || random.nextBoolean())) {
                level.setBlock(cursor, block("ore_bedrock"), Block.UPDATE_CLIENTS);
                changed++;
            }
        }
        for (int x = centerX - 3; x <= centerX + 3; x++) for (int z = centerZ - 3; z <= centerZ + 3; z++) {
            for (int above = 1; above < 7; above++) {
                cursor.set(x, bottom + above, z);
                BlockState state = level.getBlockState(cursor);
                if ((above < 3 || state.is(Blocks.BEDROCK))
                        && (LegacyOreGeneration.stone(state) || state.is(Blocks.BEDROCK))) {
                    level.setBlock(cursor, block("stone_depth"), Block.UPDATE_CLIENTS);
                    changed++;
                }
            }
        }
        return changed;
    }

    private static int veins(WorldGenLevel level, RandomSource random, int x, int z, int count, int size,
                             int min, int range, String id, Predicate<BlockState> target) {
        return LegacyOreGeneration.veins(level, random, x, z, count, size, min, range, block(id), target);
    }

    private static int veins(WorldGenLevel level, RandomSource random, int x, int z, int count, int size,
                             int min, int range, BlockState state, Predicate<BlockState> target) {
        return LegacyOreGeneration.veins(level, random, x, z, count, size, min, range, state, target);
    }

    private static BlockState block(String id) { return ModBlocks.legacy(id).get().defaultBlockState(); }
}
