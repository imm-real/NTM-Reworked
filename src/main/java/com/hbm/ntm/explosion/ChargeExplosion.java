package com.hbm.ntm.explosion;

import com.hbm.ntm.block.ChargeType;
import com.hbm.ntm.network.ChargeBlastPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

public final class ChargeExplosion {
    private ChargeExplosion() {
    }

    public static void detonate(ServerLevel level, BlockPos position, ChargeType type) {
        double x = position.getX() + 0.5D;
        double y = position.getY() + 0.5D;
        double z = position.getZ() + 0.5D;

        if (type.drops() == ChargeType.DropMode.DECAY) {
            level.explode(null, x, y, z, type.strength(), false, Level.ExplosionInteraction.TNT);
        } else {
            Explosion explosion = new Explosion(
                    level,
                    null,
                    x,
                    y,
                    z,
                    type.strength(),
                    false,
                    Explosion.BlockInteraction.DESTROY
            );
            if (type.damagesEntities()) {
                explosion.explode();
                explosion.clearToBlow();
            }
            processBlocks(level, explosion, allocate(level, x, y, z, type.strength(), type.resolution()), type.drops());
        }

        PacketDistributor.sendToPlayersNear(
                level,
                null,
                x,
                y,
                z,
                Math.max(300.0D, type.effectSize() == ChargeType.EffectSize.LARGE ? 150.0D : 200.0D),
                new ChargeBlastPayload(x, y + (type.effectSize() == ChargeType.EffectSize.LARGE ? 0.5D : 0.0D),
                        z, type.effectSize() == ChargeType.EffectSize.LARGE)
        );
    }

    /** VNT machine failure that destroys blocks without producing drops. */
    public static void detonateNoDrop(ServerLevel level, BlockPos position, float strength, int resolution) {
        double x = position.getX() + 0.5D;
        double y = position.getY() + 0.5D;
        double z = position.getZ() + 0.5D;
        Explosion explosion = new Explosion(level, null, x, y, z, strength, false,
                Explosion.BlockInteraction.DESTROY);
        explosion.explode();
        explosion.clearToBlow();
        processBlocks(level, explosion, allocate(level, x, y, z, strength, resolution), ChargeType.DropMode.NONE);
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, 300.0D,
                new ChargeBlastPayload(x, y + 0.5D, z, true));
    }

    private static Set<BlockPos> allocate(ServerLevel level, double x, double y, double z,
                                          float strength, int resolution) {
        Set<BlockPos> affected = new HashSet<>();
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                for (int k = 0; k < resolution; k++) {
                    if (i != 0 && i != resolution - 1 && j != 0 && j != resolution - 1
                            && k != 0 && k != resolution - 1) {
                        continue;
                    }
                    double dx = (double) i / (resolution - 1) * 2.0D - 1.0D;
                    double dy = (double) j / (resolution - 1) * 2.0D - 1.0D;
                    double dz = (double) k / (resolution - 1) * 2.0D - 1.0D;
                    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= length;
                    dy /= length;
                    dz /= length;

                    float power = strength * (0.7F + level.random.nextFloat() * 0.6F);
                    double currentX = x;
                    double currentY = y;
                    double currentZ = z;
                    while (power > 0.0F) {
                        BlockPos current = BlockPos.containing(currentX, currentY, currentZ);
                        if (!level.isInWorldBounds(current)) {
                            break;
                        }
                        BlockState state = level.getBlockState(current);
                        if (!state.isAir()) {
                            power -= (state.getBlock().getExplosionResistance() + 0.3F) * 0.3F;
                        }
                        if (power > 0.0F) {
                            affected.add(current.immutable());
                        }
                        currentX += dx * 0.3D;
                        currentY += dy * 0.3D;
                        currentZ += dz * 0.3D;
                        power -= 0.225F;
                    }
                }
            }
        }
        return affected;
    }

    private static void processBlocks(ServerLevel level, Explosion explosion, Set<BlockPos> affected,
                                      ChargeType.DropMode dropMode) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        if (dropMode == ChargeType.DropMode.FORTUNE_THREE) {
            var fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
            tool.enchant(fortune, 3);
        }

        for (BlockPos pos : affected) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            if (dropMode != ChargeType.DropMode.NONE && state.canDropFromExplosion(level, pos, explosion)) {
                for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity, null, tool)) {
                    Block.popResource(level, pos, drop);
                }
            }

            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            state.getBlock().wasExploded(level, pos, explosion);
        }
    }
}
