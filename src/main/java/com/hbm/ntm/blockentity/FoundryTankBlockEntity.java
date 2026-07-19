package com.hbm.ntm.blockentity;

import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.foundry.MoltenAcceptor;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Four-block foundry storage basin. Stacks fill downward, then feed outlets and adjacent basins. */
public final class FoundryTankBlockEntity extends AbstractFoundryBlockEntity {
    public static final int CAPACITY = FoundryMaterial.BLOCK * 4;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private int nextUpdate = 5;

    public FoundryTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FOUNDRY_TANK.get(), pos, state);
    }

    @Override public int capacity() { return CAPACITY; }

    public double moltenSurfaceHeight() {
        return .125D + (double) amount * .75D / CAPACITY;
    }

    @Override public boolean canAcceptFlow(FoundryMaterial incoming, int offered, Direction inputSide) {
        return false;
    }

    @Override public int acceptFlow(FoundryMaterial incoming, int offered, Direction inputSide) { return 0; }

    public static void tick(Level level, BlockPos pos, BlockState state, FoundryTankBlockEntity tank) {
        if (level.isClientSide) return;
        tank.normalize();
        if (tank.amount <= 0 || tank.material == null) {
            tank.nextUpdate = 5;
            return;
        }
        if (--tank.nextUpdate > 0) return;
        tank.nextUpdate = 5 + level.random.nextInt(6);

        if (level.getBlockEntity(pos.below()) instanceof FoundryTankBlockEntity below
                && (below.material == null || below.material == tank.material || below.amount == 0)) {
            int accepted = below.standardAccept(tank.material, tank.amount);
            if (accepted > 0) {
                tank.removeMolten(accepted);
                return;
            }
        }

        int offset = Math.floorMod((int) (level.getGameTime() + pos.asLong()), HORIZONTAL.length);
        for (int index = 0; index < HORIZONTAL.length; index++) {
            Direction direction = HORIZONTAL[(index + offset) % HORIZONTAL.length];
            BlockEntity target = level.getBlockEntity(pos.relative(direction));
            if (!(target instanceof MoltenAcceptor acceptor)
                    || target instanceof FoundryChannelBlockEntity || target instanceof FoundryTankBlockEntity) continue;
            Direction side = direction.getOpposite();
            if (!acceptor.canAcceptFlow(tank.material, tank.amount, side)) continue;
            int accepted = acceptor.acceptFlow(tank.material, tank.amount, side);
            if (accepted > 0) {
                tank.removeMolten(accepted);
                return;
            }
        }

        for (int index = 0; index < HORIZONTAL.length; index++) {
            Direction direction = HORIZONTAL[(index + offset) % HORIZONTAL.length];
            if (!(level.getBlockEntity(pos.relative(direction)) instanceof FoundryTankBlockEntity neighbor)) continue;
            if (neighbor.amount > 0 && neighbor.material != tank.material) continue;
            int difference = tank.amount - neighbor.amount;
            if (difference <= 1) continue;
            int accepted = neighbor.standardAccept(tank.material, difference / 2);
            if (accepted > 0) {
                tank.removeMolten(accepted);
                return;
            }
        }
    }
}
