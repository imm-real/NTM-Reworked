package com.hbm.ntm.explosion;

import com.hbm.ntm.blockentity.BombMultiBlockEntity;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Multi-purpose bomb. Some purposes sold separately. */
public final class MultiBombExplosion {
    /** Eight units of baseline poor judgment. */
    public static final float BASE_STRENGTH = 8.0F;

    private MultiBombExplosion() {
    }

    /** Two modifiers enter, several numbers leave. */
    public record Config(float explosionValue, int clusterCount, int fireRadius, int poisonRadius,
                         int gasCloud) {
    }

    /** Both modifier slots count, even when they contain the same bad idea. */
    public static Config config(int type2, int type5) {
        float explosionValue = BASE_STRENGTH;
        int clusterCount = 0;
        int fireRadius = 0;
        int poisonRadius = 0;
        int gasCloud = 0;
        for (int type : new int[]{type2, type5}) {
            switch (type) {
                case 1 -> explosionValue += 1.0F;
                case 2 -> explosionValue += 4.0F;
                case 3 -> clusterCount += 50;
                case 4 -> fireRadius += 10;
                case 5 -> poisonRadius += 15;
                case 6 -> gasCloud += 50;
                default -> { }
            }
        }
        return new Config(explosionValue, clusterCount, fireRadius, poisonRadius, gasCloud);
    }

    /** Eat inventory, remove bomb, distribute consequences. */
    public static DetonationResult detonate(ServerLevel level, BlockPos pos, BombMultiBlockEntity bomb) {
        if (!bomb.isLoaded()) {
            return DetonationResult.ERROR_MISSING_COMPONENT;
        }

        Config config = config(bomb.return2type(), bomb.return5type());

        bomb.clearForDetonation();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        level.removeBlock(pos, false);

        // TODO old cloud, rubble and shrapnel decorations
        level.explode(null, x, y, z, config.explosionValue(), false, Level.ExplosionInteraction.TNT);

        if (config.clusterCount() > 0) {
            // TODO Catapult submunitions; pellet_cluster is also still imaginary
            spawnClusterBomb(level, x, y, z, config.clusterCount());
        }

        if (config.fireRadius() > 0) {
            igniteAllBlocks(level, x, y, z, config.fireRadius());
        }

        if (config.poisonRadius() > 0) {
            wasteNoSchrab(level, x, y, z, config.poisonRadius());
        }

        if (config.gasCloud() > 0) {
            // TODO EntityMist; gas bombs currently forget the gas
            spawnGasCloud(level, x, y, z, config.gasCloud());
        }

        return DetonationResult.DETONATED;
    }

    @SuppressWarnings("unused")
    private static void spawnClusterBomb(ServerLevel level, int x, int y, int z, int clusterCount) {
        // TODO spawn the smaller, angrier bombs
    }

    @SuppressWarnings("unused")
    private static void spawnGasCloud(ServerLevel level, int x, int y, int z, int gasCloud) {
        // TODO poison the air properly
    }

    /** Put fire anywhere with enough floor beneath it. */
    public static void igniteAllBlocks(Level level, int x, int y, int z, int radius) {
        int r = radius;
        int r2 = r * r;
        int r22 = r2 / 2;
        BlockPos.MutableBlockPos here = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (int xx = -r; xx < r; xx++) {
            int worldX = xx + x;
            int xxSq = xx * xx;
            for (int yy = -r; yy < r; yy++) {
                int worldY = yy + y;
                int xySq = xxSq + yy * yy;
                for (int zz = -r; zz < r; zz++) {
                    int worldZ = zz + z;
                    int distSq = xySq + zz * zz;
                    if (distSq < r22) {
                        above.set(worldX, worldY + 1, worldZ);
                        BlockState aboveState = level.getBlockState(above);
                        here.set(worldX, worldY, worldZ);
                        if ((aboveState.isAir() || aboveState.is(Blocks.SNOW))
                                && !level.getBlockState(here).isAir()) {
                            level.setBlockAndUpdate(above.immutable(), Blocks.FIRE.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    /** Convert the scenery into the less healthy scenery set. */
    public static void wasteNoSchrab(ServerLevel level, int x, int y, int z, int radius) {
        int r = radius;
        int r2 = r * r;
        int r22 = r2 / 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int xx = -r; xx < r; xx++) {
            int worldX = xx + x;
            int xxSq = xx * xx;
            for (int yy = -r; yy < r; yy++) {
                int worldY = yy + y;
                int xySq = xxSq + yy * yy;
                for (int zz = -r; zz < r; zz++) {
                    int worldZ = zz + z;
                    int distSq = xySq + zz * zz;
                    // Tiny test radii would divide by zero. Gameplay bombs have more dignity.
                    int jitter = r22 / 5 > 0 ? level.random.nextInt(r22 / 5) : 0;
                    if (distSq < r22 + jitter) {
                        cursor.set(worldX, worldY, worldZ);
                        if (!level.getBlockState(cursor).isAir()) {
                            wasteDestNoSchrab(level, cursor.immutable());
                        }
                    }
                }
            }
        }
    }

    /** Per-block irradiated makeover. */
    public static void wasteDestNoSchrab(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (state.is(Blocks.GLASS) || block instanceof StainedGlassBlock
                || state.is(BlockTags.WOODEN_DOORS) || state.is(Blocks.IRON_DOOR)
                || state.is(BlockTags.LEAVES)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        } else if (state.is(Blocks.GRASS_BLOCK)) {
            level.setBlockAndUpdate(pos, ModBlocks.WASTE_EARTH.get().defaultBlockState());
        } else if (state.is(Blocks.MYCELIUM)) {
            level.setBlockAndUpdate(pos, ModBlocks.WASTE_MYCELIUM.get().defaultBlockState());
        } else if (state.is(Blocks.SAND)) {
            if (level.random.nextInt(20) == 1) {
                level.setBlockAndUpdate(pos, ModBlocks.WASTE_TRINITITE.get().defaultBlockState());
            }
        } else if (state.is(Blocks.RED_SAND)) {
            if (level.random.nextInt(20) == 1) {
                level.setBlockAndUpdate(pos, ModBlocks.WASTE_TRINITITE_RED.get().defaultBlockState());
            }
        } else if (state.is(Blocks.CLAY)) {
            level.setBlockAndUpdate(pos, Blocks.TERRACOTTA.defaultBlockState());
        } else if (state.is(Blocks.MOSSY_COBBLESTONE)) {
            level.setBlockAndUpdate(pos, Blocks.COAL_ORE.defaultBlockState());
        } else if (state.is(Blocks.COAL_ORE)) {
            int roll = level.random.nextInt(30);
            if (roll == 1 || roll == 2 || roll == 3) {
                level.setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState());
            }
            if (roll == 29) {
                level.setBlockAndUpdate(pos, Blocks.EMERALD_ORE.defaultBlockState());
            }
        } else if (state.is(Blocks.MUSHROOM_STEM)) {
            level.setBlockAndUpdate(pos, ModBlocks.WASTE_LOG.get().defaultBlockState());
        } else if (state.is(BlockTags.LOGS_THAT_BURN)) {
            level.setBlockAndUpdate(pos, ModBlocks.WASTE_LOG.get().defaultBlockState());
        } else if (state.is(BlockTags.PLANKS)) {
            level.setBlockAndUpdate(pos, ModBlocks.WASTE_PLANKS.get().defaultBlockState());
        } else if (state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }
}
