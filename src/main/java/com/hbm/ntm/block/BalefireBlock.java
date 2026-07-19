package com.hbm.ntm.block;

import com.hbm.ntm.radiation.RadiationSystem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Green fire that spreads badly, burns longer and irradiates the curious. */
public final class BalefireBlock extends FireBlock {
    private static final MapCodec<BalefireBlock> CODEC = simpleCodec(BalefireBlock::new);

    /** Amplifier nine in old potion math: half a RAD per tick. */
    private static final float RADIATION_DOSE = 0.5F;

    public BalefireBlock(Properties properties) {
        super(properties);
    }

    // The codec lies about FireBlock; the constructor still returns green trouble.
    @SuppressWarnings("unchecked")
    @Override
    public MapCodec<FireBlock> codec() {
        return (MapCodec<FireBlock>) (MapCodec<?>) CODEC;
    }

    // Custom spread. Early returns would make this fire develop standards.
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return;

        if (!state.canSurvive(level, pos)) {
            level.removeBlock(pos, false);
        }

        int meta = state.getValue(AGE);

        if (meta < 15) {
            // Thirty ticks plus scheduling noise.
            level.scheduleTick(pos, this, 30 + random.nextInt(10));
        }

        if (!canNeighborBurn(level, pos) && !level.getBlockState(pos.below())
                .isFaceSturdy(level, pos.below(), Direction.UP)) {
            level.removeBlock(pos, false);
        } else if (meta < 15) {
            tryCatchFire(level, pos.east(), 500, random, meta, Direction.WEST);
            tryCatchFire(level, pos.west(), 500, random, meta, Direction.EAST);
            tryCatchFire(level, pos.below(), 300, random, meta, Direction.UP);
            tryCatchFire(level, pos.above(), 300, random, meta, Direction.DOWN);
            tryCatchFire(level, pos.north(), 500, random, meta, Direction.SOUTH);
            tryCatchFire(level, pos.south(), 500, random, meta, Direction.NORTH);

            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            int h = 3;

            for (int ix = x - h; ix <= x + h; ix++) {
                for (int iz = z - h; iz <= z + h; iz++) {
                    for (int iy = y - 1; iy <= y + 4; iy++) {

                        if (ix == x && iy == y && iz == z) continue;

                        int fireLimit = 100;
                        if (iy > y + 1) {
                            fireLimit += (iy - (y + 1)) * 100;
                        }

                        BlockPos cell = new BlockPos(ix, iy, iz);
                        BlockState cellState = level.getBlockState(cell);

                        if (cellState.is(this) && cellState.getValue(AGE) > meta + 1) {
                            level.setBlock(cell, defaultBlockState().setValue(AGE, meta + 1), 3);
                            continue;
                        }

                        int neighborFireChance = getChanceOfNeighborsEncouragingFire(level, cell);

                        if (neighborFireChance > 0) {
                            int adjustedFireChance =
                                    (neighborFireChance + 40 + level.getDifficulty().getId() * 7) / (meta + 30);

                            if (adjustedFireChance > 0 && random.nextInt(fireLimit) <= adjustedFireChance) {
                                level.setBlock(cell, defaultBlockState().setValue(AGE, meta + 1), 3);
                            }
                        }
                    }
                }
            }
        }
    }

    private void tryCatchFire(ServerLevel level, BlockPos pos, int chance, RandomSource rand,
                              int fireAge, Direction face) {
        BlockState target = level.getBlockState(pos);
        int flammability = target.getFlammability(level, pos, face);

        if (rand.nextInt(chance) < flammability) {
            boolean tnt = target.is(Blocks.TNT);
            level.setBlock(pos, defaultBlockState().setValue(AGE, Math.min(15, fireAge + 1)), 3);

            if (tnt) {
                // Green fire gives TNT the ordinary fuse. How considerate.
                PrimedTnt primed = new PrimedTnt(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, null);
                level.addFreshEntity(primed);
            }
        }
    }

    private boolean canNeighborBurn(ServerLevel level, BlockPos pos) {
        return canCatchFire(level, pos.east(), Direction.WEST)
                || canCatchFire(level, pos.west(), Direction.EAST)
                || canCatchFire(level, pos.below(), Direction.UP)
                || canCatchFire(level, pos.above(), Direction.DOWN)
                || canCatchFire(level, pos.north(), Direction.SOUTH)
                || canCatchFire(level, pos.south(), Direction.NORTH);
    }

    private boolean canCatchFire(ServerLevel level, BlockPos pos, Direction face) {
        return level.getBlockState(pos).isFlammable(level, pos, face);
    }

    private int getChanceOfNeighborsEncouragingFire(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            return 0;
        }
        int spread = 0;
        spread = encourage(level, pos.east(), spread, Direction.WEST);
        spread = encourage(level, pos.west(), spread, Direction.EAST);
        spread = encourage(level, pos.below(), spread, Direction.UP);
        spread = encourage(level, pos.above(), spread, Direction.DOWN);
        spread = encourage(level, pos.north(), spread, Direction.SOUTH);
        spread = encourage(level, pos.south(), spread, Direction.NORTH);
        return spread;
    }

    private int encourage(ServerLevel level, BlockPos pos, int current, Direction face) {
        return Math.max(current, level.getBlockState(pos).getFireSpreadSpeed(level, pos, face));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        entity.igniteForSeconds(10.0F);
        if (!level.isClientSide && entity instanceof LivingEntity living) {
            RadiationSystem.contaminate(living, RADIATION_DOSE, true);
        }
    }
}
