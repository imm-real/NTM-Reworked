package com.hbm.ntm.entity;

import com.hbm.ntm.conveyor.ConveyorBelt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Shared server-authoritative movement for loose items and conveyor packages. */
public abstract class MovingConveyorObjectEntity extends Entity {
    public static final double BASE_SPEED = 0.0625D;

    protected MovingConveyorObjectEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);
        if (level().isClientSide || !isAlive()) {
            return;
        }
        if (tickCount <= 5) {
            return;
        }

        if ((tickCount + getId()) % 400 == 0 && handleCramming()) {
            return;
        }

        BlockPos beltPos = BlockPos.containing(position());
        BlockState state = level().getBlockState(beltPos);
        if (!(state.getBlock() instanceof ConveyorBelt belt)
                || !belt.canItemStay(level(), beltPos, state, position())) {
            leaveConveyor();
            return;
        }

        Vec3 oldPosition = position();
        BlockPos oldBlock = BlockPos.containing(oldPosition);
        Vec3 target = belt.travelLocation(level(), beltPos, state, oldPosition, BASE_SPEED);
        Vec3 motion = target.subtract(oldPosition);
        setDeltaMovement(motion);
        setPos(target.x, target.y, target.z);

        BlockPos newBlock = BlockPos.containing(target);
        if (!oldBlock.equals(newBlock)) {
            Direction incomingSide = incomingSide(oldBlock, newBlock);
            if (tryEnter(newBlock, incomingSide) || !isAlive()) {
                return;
            }
            BlockState enteredState = level().getBlockState(newBlock);
            if (!enteredState.canOcclude() && !(enteredState.getBlock() instanceof ConveyorBelt)) {
                tryEnter(newBlock.below(), Direction.UP);
            }
        }
    }

    private boolean handleCramming() {
        List<MovingConveyorObjectEntity> nearby = level().getEntitiesOfClass(
                MovingConveyorObjectEntity.class, getBoundingBox().inflate(0.125D));
        if (nearby.size() < 25) {
            return false;
        }
        nearby.forEach(Entity::discard);
        BlockPos pos = blockPosition();
        level().explode(this, getX(), getY() + 0.125D, getZ(), 1.0F,
                Level.ExplosionInteraction.BLOCK);
        if (level().getBlockState(pos).getBlock() instanceof ConveyorBelt) {
            level().destroyBlock(pos, false);
        }
        return true;
    }

    private static Direction incomingSide(BlockPos oldPos, BlockPos newPos) {
        int dx = newPos.getX() - oldPos.getX();
        int dy = newPos.getY() - oldPos.getY();
        int dz = newPos.getZ() - oldPos.getZ();
        if (dx > 0) return Direction.WEST;
        if (dx < 0) return Direction.EAST;
        if (dy > 0) return Direction.DOWN;
        if (dy < 0) return Direction.UP;
        if (dz > 0) return Direction.NORTH;
        if (dz < 0) return Direction.SOUTH;
        return Direction.UP;
    }

    protected abstract boolean tryEnter(BlockPos pos, Direction incomingSide);

    protected abstract void leaveConveyor();

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
