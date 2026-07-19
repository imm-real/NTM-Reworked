package com.hbm.ntm.block;

import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.hazard.HazardSystem;
import com.hbm.ntm.radiation.ModDamageTypes;
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

/** Source Carbon Monoxide gas emitted by harvested Nether Coal Ore. */
public final class CarbonMonoxideGasBlock extends Block {
    private static final int INITIAL_DELAY = 10;
    private static final int MOVE_DELAY = 2;

    public CarbonMonoxideGasBlock(Properties properties) {
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
        if (random.nextInt(100) == 0) {
            level.removeBlock(pos, false);
            return;
        }
        if (!tryMove(level, pos, Direction.DOWN)
                && !tryMove(level, pos, Direction.Plane.HORIZONTAL.getRandomDirection(random))) {
            level.scheduleTick(pos, this, MOVE_DELAY);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living)) return;
        if (HazardSystem.hasProtection(living, HazardProtection.GAS_MONOXIDE, 1)) return;
        living.hurt(level.damageSources().source(ModDamageTypes.MONOXIDE), 1.0F);
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
