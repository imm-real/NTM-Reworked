package com.hbm.ntm.mixin.compat.sable;

import com.hbm.ntm.block.TurbofanBlock;
import com.hbm.ntm.blockentity.TurbofanBlockEntity;
import com.hbm.ntm.compat.TurbofanAirflowFrame;
import com.hbm.ntm.compat.TurbofanVehiclePhysics;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;

/** Makes a running HBM turbofan a native Sable/Aeronautics propulsion actor. */
@Mixin(TurbofanBlockEntity.class)
public abstract class TurbofanSableMixin extends BlockEntity
        implements BlockEntitySubLevelPropellerActor, BlockEntityPropeller, TurbofanAirflowFrame {
    protected TurbofanSableMixin(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state);
    }

    private TurbofanBlockEntity hbm$turbofan() {
        return (TurbofanBlockEntity) (Object) this;
    }

    private SubLevel hbm$subLevel() {
        return level == null ? null : Sable.HELPER.getContaining(level, worldPosition);
    }

    @Override
    public Vec3 hbm$localVectorToWorld(Vec3 localVector) {
        SubLevel subLevel = hbm$subLevel();
        return subLevel == null ? localVector : subLevel.logicalPose().transformNormal(localVector);
    }

    @Override
    public AABB hbm$worldBoundsToLocal(AABB worldBounds) {
        SubLevel subLevel = hbm$subLevel();
        return subLevel == null ? worldBounds : new BoundingBox3d(worldBounds)
                .transformInverse(subLevel.logicalPose(), new BoundingBox3d())
                .toMojang();
    }

    @Override
    public double hbm$distanceSquaredToLocalPosition(Vec3 observerPosition, Vec3 localPosition) {
        return level == null
                ? observerPosition.distanceToSqr(localPosition)
                : Sable.HELPER.distanceSquaredWithSubLevels(
                        level, observerPosition, localPosition.x, localPosition.y, localPosition.z);
    }

    @Override
    public BlockEntityPropeller getPropeller() {
        return this;
    }

    @Override
    public Direction getBlockDirection() {
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(TurbofanBlock.FACING)
                ? state.getValue(TurbofanBlock.FACING) : Direction.NORTH;
        return TurbofanVehiclePhysics.exhaustDirection(facing);
    }

    @Override
    public double getAirflow() {
        return TurbofanVehiclePhysics.airflow(hbm$turbofan().output());
    }

    @Override
    public double getThrust() {
        return TurbofanVehiclePhysics.thrust(hbm$turbofan().output());
    }

    @Override
    public boolean isActive() {
        TurbofanBlockEntity turbofan = hbm$turbofan();
        return TurbofanVehiclePhysics.isActive(
                turbofan.wasOn(), turbofan.output(), turbofan.consumption());
    }

    /**
     * The block entity occupies the bottom-center block, while the rotor axis is one block higher.
     * Applying at the actual axis prevents a centered engine from inventing pitch torque.
     */
    @Override
    public void applyForces(ServerSubLevel subLevel, Vec3 thrustDirection, double timeStep) {
        double scaledThrust = getScaledThrust() * timeStep;
        Vector3d thrust = new Vector3d(thrustDirection.x, thrustDirection.y, thrustDirection.z)
                .mul(scaledThrust);
        BlockPos position = getBlockPos();
        Vector3d rotorCenter = new Vector3d(
                position.getX() + 0.5D, position.getY() + 1.5D, position.getZ() + 0.5D);
        ForceGroup propulsion = ForceGroups.REGISTRY.get(
                ResourceLocation.fromNamespaceAndPath("sable", "propulsion"));
        if (propulsion == null) return;
        QueuedForceGroup forceGroup = subLevel.getOrCreateQueuedForceGroup(propulsion);
        forceGroup.applyAndRecordPointForce(rotorCenter, thrust);
    }
}
