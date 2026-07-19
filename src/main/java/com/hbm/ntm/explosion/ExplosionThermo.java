package com.hbm.ntm.explosion;

import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Hot, cold and the exciting new third option: property damage. */
public final class ExplosionThermo {
    private ExplosionThermo() {
    }

    /** Turns the neighborhood into a freezer with structural damage. */
    public static void freeze(ServerLevel level, int x, int y, int z, int bombStartStrength) {
        int r = bombStartStrength * 2;
        int r2 = r * r;
        int r22 = r2 / 2;
        for (int xx = -r; xx < r; xx++) {
            int X = xx + x;
            int XX = xx * xx;
            for (int yy = -r; yy < r; yy++) {
                int Y = yy + y;
                int YY = XX + yy * yy;
                for (int zz = -r; zz < r; zz++) {
                    int Z = zz + z;
                    int ZZ = YY + zz * zz;
                    int jitter = r22 / 2 > 0 ? level.random.nextInt(r22 / 2) : 0;
                    if (ZZ < r22 + jitter) {
                        freezeDest(level, X, Y, Z);
                    }
                }
            }
        }
    }

    /** Same ball, opposite thermostat. */
    public static void scorch(ServerLevel level, int x, int y, int z, int bombStartStrength) {
        int r = bombStartStrength * 2;
        int r2 = r * r;
        int r22 = r2 / 2;
        for (int xx = -r; xx < r; xx++) {
            int X = xx + x;
            int XX = xx * xx;
            for (int yy = -r; yy < r; yy++) {
                int Y = yy + y;
                int YY = XX + yy * yy;
                for (int zz = -r; zz < r; zz++) {
                    int Z = zz + z;
                    int ZZ = YY + zz * zz;
                    int jitter = r22 / 2 > 0 ? level.random.nextInt(r22 / 2) : 0;
                    if (ZZ < r22 + jitter) {
                        scorchDest(level, X, Y, Z);
                    }
                }
            }
        }
    }

    /** One lookup only. Re-freezing the output would be cheating. */
    public static void freezeDest(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);

        if (state.is(Blocks.GRASS_BLOCK)) {
            level.setBlockAndUpdate(pos, ModBlocks.FROZEN_GRASS.get().defaultBlockState());
        } else if (state.is(Blocks.DIRT)) {
            level.setBlockAndUpdate(pos, ModBlocks.FROZEN_DIRT.get().defaultBlockState());
        } else if (state.is(BlockTags.LOGS_THAT_BURN) || state.is(ModBlocks.WASTE_LOG.get())) {
            level.setBlockAndUpdate(pos, ModBlocks.FROZEN_LOG.get().defaultBlockState());
        } else if (state.is(BlockTags.PLANKS) || state.is(ModBlocks.WASTE_PLANKS.get())) {
            level.setBlockAndUpdate(pos, ModBlocks.FROZEN_PLANKS.get().defaultBlockState());
        } else if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.MOSSY_STONE_BRICKS) || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.CHISELED_STONE_BRICKS)) {
            level.setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState());
        } else if (state.is(BlockTags.LEAVES)) {
            level.setBlockAndUpdate(pos, Blocks.SNOW_BLOCK.defaultBlockState());
        } else if (state.is(Blocks.LAVA)) {
            level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
        } else if (state.is(Blocks.WATER)) {
            level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
        }
    }

    /** Undo winter using entirely reasonable amounts of fire. */
    public static void scorchDest(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);

        if (state.is(Blocks.GRASS_BLOCK) || state.is(ModBlocks.FROZEN_GRASS.get())) {
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        } else if (state.is(Blocks.DIRT)) {
            level.setBlockAndUpdate(pos, Blocks.NETHERRACK.defaultBlockState());
        } else if (state.is(ModBlocks.FROZEN_DIRT.get())) {
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        } else if (state.is(Blocks.NETHERRACK)) {
            level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
        } else if (state.is(BlockTags.LOGS_THAT_BURN) || state.is(ModBlocks.WASTE_LOG.get())
                || state.is(ModBlocks.FROZEN_LOG.get())) {
            level.setBlockAndUpdate(pos, ModBlocks.WASTE_LOG.get().defaultBlockState());
        } else if (state.is(ModBlocks.FROZEN_PLANKS.get()) || state.is(BlockTags.PLANKS)
                || state.is(ModBlocks.WASTE_PLANKS.get())) {
            level.setBlockAndUpdate(pos, ModBlocks.WASTE_PLANKS.get().defaultBlockState());
        } else if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.MOSSY_STONE_BRICKS) || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.CHISELED_STONE_BRICKS) || state.is(Blocks.OBSIDIAN)) {
            level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
        } else if (state.is(BlockTags.LEAVES)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        } else if (state.is(Blocks.WATER)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        } else if (state.is(Blocks.PACKED_ICE)) {
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        } else if (state.is(Blocks.ICE)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    /** Cats are immune. The ice prison is off-center. Both are features. */
    public static void freezer(ServerLevel level, int x, int y, int z, int bombStartStrength) {
        double wat = bombStartStrength;
        int doubled = bombStartStrength * 2;

        int i = Mth.floor(x - wat - 1.0D);
        int j = Mth.floor(x + wat + 1.0D);
        int k = Mth.floor(y - wat - 1.0D);
        int i2 = Mth.floor(y + wat + 1.0D);
        int l = Mth.floor(z - wat - 1.0D);
        int j2 = Mth.floor(z + wat + 1.0D);
        AABB box = new AABB(i, k, l, j, i2, j2);
        List<Entity> list = level.getEntities((Entity) null, box);

        for (Entity entity : list) {
            double d4 = Math.sqrt(entity.distanceToSqr(x, y, z)) / doubled;

            if (d4 <= 1.0D) {
                double d5 = entity.getX() - x;
                double d6 = entity.getY() + entity.getEyeHeight() - y;
                double d7 = entity.getZ() - z;
                double d9 = Math.sqrt(d5 * d5 + d6 * d6 + d7 * d7);
                if (d9 < wat && !(entity instanceof Ocelot) && !(entity instanceof Cat)
                        && entity instanceof LivingEntity living) {
                    int px = (int) entity.getX();
                    int py = (int) entity.getY();
                    int pz = (int) entity.getZ();
                    for (int a = px - 2; a < px + 1; a++) {
                        for (int b = py; b < py + 3; b++) {
                            for (int c = pz - 1; c < pz + 2; c++) {
                                level.setBlockAndUpdate(new BlockPos(a, b, c), Blocks.ICE.defaultBlockState());
                            }
                        }
                    }

                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 2 * 60 * 20, 4));
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 90 * 20, 2));
                    living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 3 * 60 * 20, 2));
                }
            }
        }
    }

    /** Ten seconds of fire. Asbestos owners may laugh later. */
    public static void setEntitiesOnFire(ServerLevel level, double x, double y, double z, int radius) {
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        List<Entity> list = level.getEntities((Entity) null, box);

        for (Entity e : list) {
            if (Math.sqrt(e.distanceToSqr(x, y, z)) <= radius) {
                if (!(e instanceof Player player && wearsAsbestosSuit(player))) {
                    if (e instanceof LivingEntity living) {
                        living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 15 * 20, 4));
                    }
                    e.igniteForSeconds(10.0F);
                }
            }
        }
    }

    // TODO asbestos, the fashionable solution to being on fire
    private static boolean wearsAsbestosSuit(Player player) {
        return false;
    }
}
