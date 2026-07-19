package com.hbm.ntm.explosion;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Gravity is more of a suggestion here. */
public final class ExplosionChaos {
    private ExplosionChaos() {
    }

    /** Moves the dirt, the bedrock and your machines. The tile data stays downstairs. */
    public static void floater(ServerLevel level, int x, int y, int z, int radi, int height) {
        int r = radi;
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
                    if (ZZ < r22) {
                        BlockPos from = new BlockPos(X, Y, Z);
                        BlockState save = level.getBlockState(from);
                        level.setBlock(from, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        if (!save.isAir()) {
                            level.setBlock(new BlockPos(X, Y + height, Z), save, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }

    /** Flips the livestock, then sends everyone upstairs. Very important bomb science. */
    public static void move(ServerLevel level, int x, int y, int z, int radius, int a, int b, int c) {
        double wat = radius;
        radius *= 2;

        int i = Mth.floor(x - wat - 1.0D);
        int j = Mth.floor(x + wat + 1.0D);
        int k = Mth.floor(y - wat - 1.0D);
        int i2 = Mth.floor(y + wat + 1.0D);
        int l = Mth.floor(z - wat - 1.0D);
        int j2 = Mth.floor(z + wat + 1.0D);
        AABB box = new AABB(i, k, l, j, i2, j2);
        List<Entity> list = level.getEntities((Entity) null, box);

        for (Entity entity : list) {
            double d4 = Math.sqrt(entity.distanceToSqr(x, y, z)) / radius;

            if (d4 <= 1.0D) {
                double d5 = entity.getX() - x;
                double d6 = entity.getY() + entity.getEyeHeight() - y;
                double d7 = entity.getZ() - z;

                if (entity instanceof Mob && !(entity instanceof Sheep)) {
                    if (level.random.nextInt(2) == 0) {
                        entity.setCustomName(Component.literal("Dinnerbone"));
                    } else {
                        entity.setCustomName(Component.literal("Grumm"));
                    }
                }

                if (entity instanceof Sheep) {
                    entity.setCustomName(Component.literal("jeb_"));
                }

                double d9 = Math.sqrt(d5 * d5 + d6 * d6 + d7 * d7);
                if (d9 < wat) {
                    double nx = entity.getX() + a;
                    double ny = entity.getY() + b;
                    double nz = entity.getZ() + c;
                    if (entity instanceof ServerPlayer player) {
                        player.connection.teleport(nx, ny, nz, player.getYRot(), player.getXRot());
                    } else {
                        entity.setPos(nx, ny, nz);
                    }
                }
            }
        }
    }
}
