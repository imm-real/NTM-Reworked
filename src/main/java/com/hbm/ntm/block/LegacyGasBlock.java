package com.hbm.ntm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/** Invisible wandering cave gas. The explosive kind still hits with power three. */
public final class LegacyGasBlock extends Block {
    private final boolean explosive;

    public LegacyGasBlock(Properties properties, boolean explosive) {
        super(properties);
        this.explosive = explosive;
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 10);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        for (Direction direction : Direction.values()) {
            if (isIgnition(level.getBlockState(pos.relative(direction)))) {
                ignite(level, pos);
                return;
            }
        }
        if (random.nextInt(20) == 0 && level.getBlockState(pos.below()).isAir()) {
            level.removeBlock(pos, false);
            return;
        }
        Direction first = random.nextInt(3) == 0
                ? (random.nextBoolean() ? Direction.UP : Direction.DOWN)
                : Direction.Plane.HORIZONTAL.getRandomDirection(random);
        if (!move(level, pos, first)) {
            move(level, pos, Direction.Plane.HORIZONTAL.getRandomDirection(random));
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity.isOnFire()) ignite((ServerLevel) level, pos);
    }

    private boolean move(ServerLevel level, BlockPos from, Direction direction) {
        BlockPos to = from.relative(direction);
        if (!level.getBlockState(to).isAir()) {
            level.scheduleTick(from, this, 16 + level.random.nextInt(5));
            return false;
        }
        level.setBlock(to, defaultBlockState(), Block.UPDATE_ALL);
        level.removeBlock(from, false);
        level.scheduleTick(to, this, 16 + level.random.nextInt(5));
        return true;
    }

    private void ignite(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
        if (explosive) level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                3.0F, true, Level.ExplosionInteraction.NONE);
    }

    private static boolean isIgnition(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.TORCH)
                || state.is(Blocks.JACK_O_LANTERN);
    }
}
