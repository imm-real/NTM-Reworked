package com.hbm.ntm.block;

import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.hazard.HazardSystem;
import com.hbm.ntm.radiation.RadiationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Source ordinary Radon Gas emitted by Uranium Ore outgassing. */
public final class RadonGasBlock extends Block {
    private static final int INITIAL_DELAY = 10;
    private static final int MOVE_DELAY = 2;

    public RadonGasBlock(Properties properties) {
        super(properties);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                      CollisionContext context) { return Shapes.empty(); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) { return Shapes.empty(); }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) level.scheduleTick(pos, this, INITIAL_DELAY);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide) level.scheduleTick(pos, this, INITIAL_DELAY);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(50) == 0) {
            level.removeBlock(pos, false);
            return;
        }
        Direction first = random.nextInt(5) == 0 ? Direction.UP : Direction.DOWN;
        if (!tryMove(level, pos, first)
                && !tryMove(level, pos, Direction.Plane.HORIZONTAL.getRandomDirection(random))) {
            level.scheduleTick(pos, this, MOVE_DELAY);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living)) return;
        if (HazardSystem.hasProtection(living, HazardProtection.PARTICLE_FINE, 1)) return;
        RadiationSystem.contaminate(living, 0.05F, true);
        RadiationSystem.addAsbestos(living, 1);
    }

    private boolean tryMove(ServerLevel level, BlockPos from, Direction direction) {
        BlockPos to = from.relative(direction);
        if (!level.getBlockState(to).isAir()) return false;
        level.removeBlock(from, false);
        level.setBlock(to, defaultBlockState(), Block.UPDATE_ALL);
        level.scheduleTick(to, this, MOVE_DELAY);
        return true;
    }
}
