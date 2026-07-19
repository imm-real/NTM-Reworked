package com.hbm.ntm.blockentity;

import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.foundry.MoltenAcceptor;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Two-ingot open channel that prioritizes molds/outlets before equalizing with other channels. */
public final class FoundryChannelBlockEntity extends AbstractFoundryBlockEntity {
    public static final int CAPACITY = FoundryMaterial.INGOT * 2;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private int nextUpdate = 5;
    private Direction lastFlow;

    public FoundryChannelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FOUNDRY_CHANNEL.get(), pos, state);
    }

    @Override public int capacity() { return CAPACITY; }

    public double moltenSurfaceHeight() {
        return .125D + (double) amount * .25D / CAPACITY;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FoundryChannelBlockEntity channel) {
        if (level.isClientSide) return;
        channel.normalize();
        if (channel.amount <= 0 || channel.material == null) {
            channel.lastFlow = null;
            channel.nextUpdate = 5;
            return;
        }
        if (--channel.nextUpdate > 0) return;
        channel.nextUpdate = 5;

        List<Direction> directions = channel.directions(level.getGameTime());
        for (Direction direction : directions) {
            BlockEntity target = level.getBlockEntity(pos.relative(direction));
            if (!(target instanceof MoltenAcceptor acceptor) || target instanceof FoundryChannelBlockEntity) continue;
            Direction side = direction.getOpposite();
            if (!acceptor.canAcceptFlow(channel.material, channel.amount, side)) continue;
            int accepted = acceptor.acceptFlow(channel.material, channel.amount, side);
            if (accepted > 0) {
                channel.removeMolten(accepted);
                return;
            }
        }

        for (Direction direction : directions) {
            BlockEntity target = level.getBlockEntity(pos.relative(direction));
            if (!(target instanceof FoundryChannelBlockEntity neighbor)) continue;
            if (neighbor.amount > 0 && neighbor.material != channel.material) continue;
            int difference = channel.amount - neighbor.amount;
            if (difference <= 1) continue;
            int transfer = difference / 2;
            int accepted = neighbor.standardAccept(channel.material, transfer);
            if (accepted > 0) {
                channel.removeMolten(accepted);
                neighbor.lastFlow = direction.getOpposite();
                return;
            }
        }
    }

    private List<Direction> directions(long gameTime) {
        List<Direction> result = new ArrayList<>(4);
        int offset = Math.floorMod((int) (gameTime + worldPosition.asLong()), HORIZONTAL.length);
        for (int index = 0; index < HORIZONTAL.length; index++) {
            Direction direction = HORIZONTAL[(index + offset) % HORIZONTAL.length];
            if (direction != lastFlow) result.add(direction);
        }
        if (lastFlow != null) result.add(lastFlow);
        return result;
    }

    @Override public boolean canAcceptPour(FoundryMaterial incoming, int offered, Direction inputSide) {
        return inputSide == Direction.UP && networkAccepts(incoming) && standardCanAccept(incoming, offered);
    }

    private boolean networkAccepts(FoundryMaterial incoming) {
        if (level == null) return true;
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(worldPosition);
        while (!open.isEmpty() && visited.size() < 4096) {
            BlockPos current = open.removeFirst();
            if (!visited.add(current)) continue;
            if (!(level.getBlockEntity(current) instanceof FoundryChannelBlockEntity channel)) continue;
            if (channel.amount > 0 && channel.material != null && channel.material != incoming) return false;
            for (Direction direction : HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (!visited.contains(next) && level.getBlockEntity(next) instanceof FoundryChannelBlockEntity) {
                    open.addLast(next);
                }
            }
        }
        return true;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (lastFlow != null) tag.putString("last_flow", lastFlow.getName());
        tag.putInt("next_update", nextUpdate);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lastFlow = Direction.byName(tag.getString("last_flow"));
        nextUpdate = tag.contains("next_update") ? tag.getInt("next_update") : 5;
    }
}
