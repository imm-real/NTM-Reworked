package com.hbm.ntm.blockentity;

import com.hbm.ntm.entity.ChlorineCloudEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/** Exact TileEntityVent chlorine branch, including integer-truncated Gaussian offsets. */
public final class ChlorineVentBlockEntity extends BlockEntity {
    private final Random random = new Random();

    public ChlorineVentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VENT_CHLORINE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChlorineVentBlockEntity vent) {
        if (!(level instanceof ServerLevel server) || !server.hasNeighborSignal(pos)) return;

        double x = vent.random.nextGaussian() * 1.5D;
        double y = vent.random.nextGaussian() * 1.5D;
        double z = vent.random.nextGaussian() * 1.5D;
        BlockPos target = pos.offset((int) x, (int) y, (int) z);
        BlockState targetState = server.getBlockState(target);

        // Full opaque cubes stop chlorine. Glass and common sense do not.
        boolean normalCube = targetState.canOcclude()
                && targetState.isCollisionShapeFullBlock(server, target);
        if (!normalCube) {
            server.addFreshEntity(ChlorineCloudEntity.create(
                    server, target.getX(), target.getY(), target.getZ(), x / 2.0D, y / 2.0D, z / 2.0D));
        }
    }
}
