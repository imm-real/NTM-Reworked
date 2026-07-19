package com.hbm.ntm.block;

import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.ExplosionThermo;
import com.hbm.ntm.explosion.RemoteDetonatable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Instant thermobarics. No fuse, no warning, no refunds. */
public final class BombThermoBlock extends Block implements RemoteDetonatable {
    public enum Type { ENDOTHERMIC, EXOTHERMIC }

    private final Type type;

    public BombThermoBlock(Properties properties, Type type) {
        super(properties);
        this.type = type;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   BlockPos neighborPos, boolean movedByPiston) {
        // Redstone should only end the world once, so the server gets custody.
        if (!level.isClientSide && level.hasNeighborSignal(pos)) {
            detonate((ServerLevel) level, pos);
        }
    }

    @Override
    public DetonationResult detonateRemotely(ServerLevel level, BlockPos position) {
        // If you called this, congratulations: it detonated.
        detonate(level, position);
        return DetonationResult.DETONATED;
    }

    private void detonate(ServerLevel level, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        // Remove first or the bomb freezes itself. Embarrassing.
        level.removeBlock(pos, false);

        // Terrain gets 15, meat gets 20.
        if (type == Type.ENDOTHERMIC) {
            ExplosionThermo.freeze(level, x, y, z, 15);
            ExplosionThermo.freezer(level, x, y, z, 20);
        } else {
            ExplosionThermo.scorch(level, x, y, z, 15);
            ExplosionThermo.setEntitiesOnFire(level, x, y, z, 20);
        }

        // Dessert: TNT, but 25% angrier.
        level.explode(null, x, y, z, 5.0F, false, Level.ExplosionInteraction.TNT);
    }
}
