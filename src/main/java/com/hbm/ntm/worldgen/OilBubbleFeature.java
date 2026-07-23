package com.hbm.ntm.worldgen;

import com.hbm.ntm.block.DeadPlantBlock;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Finite Oil bubble with the old generator's surface scar. */
public final class OilBubbleFeature extends Feature<NoneFeatureConfiguration> {
    public static final int MIN_Y = 15;
    public static final int Y_RANGE = 25;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS_EXCLUSIVE = 16;
    public static final int SURFACE_SPOTS = 150;
    public static final int SURFACE_WIDTH = 7;

    public OilBubbleFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        int configured = HbmConfig.OIL_SPAWN_RATE.get();
        if (configured <= 0) return false;

        WorldGenLevel level = context.level();
        if (!level.getLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return false;
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        Biome biome = level.getBiome(origin).value();
        int frequency = effectiveFrequency(configured, biome.getBaseTemperature(),
                biome.getModifiedClimateSettings().downfall());
        if (random.nextInt(frequency) != frequency - 1) return false;

        int centerX = origin.getX() + random.nextInt(16);
        int centerZ = origin.getZ() + random.nextInt(16);
        int centerY = LegacyWorldgenHeights.aboveBottom(level, MIN_Y + random.nextInt(Y_RANGE));
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS_EXCLUSIVE - MIN_RADIUS);
        double radiusSquared = radius * radius / 2.0D;
        int horizontal = (int) Math.ceil(Math.sqrt(radiusSquared));
        int vertical = (int) Math.ceil(Math.sqrt(radiusSquared / 3.0D));
        ChunkPos chunk = new ChunkPos(origin);
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        int maxX = chunk.getMaxBlockX();
        int maxZ = chunk.getMaxBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;

        for (int x = Math.max(centerX - horizontal, minX);
             x <= Math.min(centerX + horizontal, maxX); x++) {
            for (int z = Math.max(centerZ - horizontal, minZ);
                 z <= Math.min(centerZ + horizontal, maxZ); z++) {
                for (int y = Math.max(level.getMinBuildHeight(), centerY - vertical);
                     y <= Math.min(level.getMaxBuildHeight() - 1, centerY + vertical); y++) {
                    int dx = x - centerX;
                    int dz = z - centerZ;
                    int dy = centerY - y;
                    if (!insideBubble(dx, dy, dz, radius)) continue;
                    cursor.set(x, y, z);
                    BlockState existing = level.getBlockState(cursor);
                    if (existing.is(Blocks.STONE) || existing.is(Blocks.DEEPSLATE)) {
                        level.setBlock(cursor, ModBlocks.ORE_OIL.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                        changed++;
                    }
                }
            }
        }

        addSurfaceSpot(level, random, centerX, centerZ, chunk);
        return changed > 0;
    }

    public static int effectiveFrequency(int configured, float temperature, float downfall) {
        int frequency = temperature >= 2.0F && downfall < 0.1F ? configured / 3 : configured;
        return Math.max(frequency, 1);
    }

    public static boolean insideBubble(int dx, int dy, int dz, int radius) {
        double radiusSquared = radius * radius / 2.0D;
        return dx * dx + dz * dz + dy * dy * 3.0D < radiusSquared;
    }

    private static void addSurfaceSpot(WorldGenLevel level, RandomSource random, int centerX, int centerZ,
                                       ChunkPos chunk) {
        for (int i = 0; i < SURFACE_SPOTS; i++) {
            int x = centerX + (int) (random.nextGaussian() * SURFACE_WIDTH);
            int z = centerZ + (int) (random.nextGaussian() * SURFACE_WIDTH);
            if (!insideChunk(x, z, chunk)) continue;
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            int distanceX = x - centerX;
            int distanceZ = z - centerZ;
            boolean inner = distanceX * distanceX + distanceZ * distanceZ < 9;
            scarColumn(level, random, x, surface, z, inner);
        }

        if (insideChunk(centerX, centerZ, chunk)) {
            carveMarker(level, centerX, centerZ, true);
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int x = centerX + direction.getStepX();
            int z = centerZ + direction.getStepZ();
            if (insideChunk(x, z, chunk)) {
                carveMarker(level, x, z, false);
            }
        }
    }

    public static boolean insideChunk(int x, int z, ChunkPos chunk) {
        return x >= chunk.getMinBlockX() && x <= chunk.getMaxBlockX()
                && z >= chunk.getMinBlockZ() && z <= chunk.getMaxBlockZ();
    }

    private static void scarColumn(WorldGenLevel level, RandomSource random, int x, int surface, int z,
                                   boolean inner) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = surface + 1; y >= surface - 2 && y >= level.getMinBuildHeight(); y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
                level.setBlock(cursor, (inner ? ModBlocks.DIRT_OILY : ModBlocks.DIRT_DEAD).get()
                        .defaultBlockState(), Block.UPDATE_CLIENTS);
                BlockPos above = cursor.above();
                if (!inner && random.nextInt(20) == 0 && level.getBlockState(above).isAir()) {
                    level.setBlock(above, ModBlocks.PLANT_DEAD.get().defaultBlockState()
                            .setValue(DeadPlantBlock.VARIANT, random.nextInt(5)), Block.UPDATE_CLIENTS);
                }
                return;
            }
            if (state.is(Blocks.SAND)) {
                level.setBlock(cursor, ModBlocks.SAND_DIRTY.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                return;
            }
            if (state.is(Blocks.RED_SAND)) {
                level.setBlock(cursor, ModBlocks.SAND_DIRTY_RED.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                return;
            }
            if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)) {
                level.setBlock(cursor, ModBlocks.STONE_CRACKED.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                return;
            }
        }
    }

    private static void carveMarker(WorldGenLevel level, int x, int z, boolean center) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        int solids = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (y >= level.getMinBuildHeight() && solids < (center ? 7 : 4)) {
            cursor.set(x, y--, z);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) continue;
            if (!state.getFluidState().isEmpty()) return;
            if (!state.isSolidRender(level, cursor)) continue;
            solids++;
            if (!center) {
                level.setBlock(cursor, ModBlocks.STONE_CRACKED.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (solids < 3) {
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (solids == 3) {
                level.setBlock(cursor, ModBlocks.OIL_SPILL.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (solids < 7) {
                level.setBlock(cursor, ModBlocks.STONE_CRACKED.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
