package com.hbm.ntm.compat;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonatable;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

/** Teaches moving Sable bombs that the ground is a detonator. */
public final class SableBombImpactFuse implements BlockSubLevelCollisionCallback {
    public static final SableBombImpactFuse INSTANCE = new SableBombImpactFuse();
    private static final String FUSE_DATA = "hbmImpactFuses";
    private static final ThreadLocal<Map<Long, Double>> RELEASE_ALTITUDES =
            ThreadLocal.withInitial(HashMap::new);

    private SableBombImpactFuse() { }

    public static void beforeMove(ServerLevel sourceLevel, BlockPos sourcePosition,
                                  BlockPos targetPosition) {
        RELEASE_ALTITUDES.get().put(targetPosition.asLong(),
                worldPosition(sourceLevel, sourcePosition).y);
    }

    public static void afterMove(ServerLevel targetLevel, BlockPos targetPosition) {
        Map<Long, Double> releases = RELEASE_ALTITUDES.get();
        Double releaseAltitude = releases.remove(targetPosition.asLong());
        if (releases.isEmpty()) RELEASE_ALTITUDES.remove();
        if (releaseAltitude == null) releaseAltitude = worldPosition(targetLevel, targetPosition).y;

        ServerSubLevel subLevel = containingSubLevel(targetLevel, targetPosition);
        if (subLevel != null) fuseData(subLevel).putDouble(positionKey(targetPosition), releaseAltitude);
    }

    @Override
    public CollisionResult sable$onCollision(BlockPos movingPosition, BlockPos otherPosition,
                                              Vector3d contactPoint, double impactSpeed) {
        try {
            return handleCollision(movingPosition, impactSpeed);
        } catch (RuntimeException exception) {
            // Do not throw through Rapier unless a poisoned mutex and JVM funeral sound enjoyable.
            HbmNtm.LOGGER.error("Failed to process Sable bomb impact at {}", movingPosition, exception);
            return CollisionResult.NONE;
        }
    }

    private CollisionResult handleCollision(BlockPos movingPosition, double impactSpeed) {
        SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        if (physics == null) return CollisionResult.NONE;
        ServerLevel level = physics.getLevel();
        ServerSubLevel subLevel = containingSubLevel(level, movingPosition);
        if (subLevel == null) return CollisionResult.NONE;

        CompoundTag fuses = fuseData(subLevel);
        String key = positionKey(movingPosition);
        double currentAltitude = worldPosition(level, movingPosition).y;
        if (!fuses.contains(key)) {
            // Old vehicles get one free baseline bump instead of an unsolicited detonation.
            fuses.putDouble(key, currentAltitude);
            return CollisionResult.NONE;
        }

        double releaseAltitude = fuses.getDouble(key);
        if (!BombImpactFusePhysics.shouldDetonate(impactSpeed, releaseAltitude, currentAltitude)) {
            return CollisionResult.NONE;
        }

        BlockState state = level.getBlockState(movingPosition);
        if (!(state.getBlock() instanceof RemoteDetonatable)) return CollisionResult.NONE;
        // Rapier is locked here. Detonate later unless mutex poisoning is the intended payload.
        BombImpactFuseEvents.queue(level, movingPosition, () -> {
            BlockState currentState = level.getBlockState(movingPosition);
            if (!(currentState.getBlock() instanceof RemoteDetonatable currentBomb)) return;
            DetonationResult result = currentBomb.detonateRemotely(level, movingPosition);
            if (result.wasSuccessful()) fuses.remove(key);
        });
        return new CollisionResult(new Vector3d(), true);
    }

    private static ServerSubLevel containingSubLevel(ServerLevel level, BlockPos position) {
        SubLevel containing = Sable.HELPER.getContaining(level, position);
        return containing instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    private static Vec3 worldPosition(ServerLevel level, BlockPos position) {
        return Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(position));
    }

    private static CompoundTag fuseData(ServerSubLevel subLevel) {
        CompoundTag userData = subLevel.getUserDataTag();
        if (userData == null) {
            userData = new CompoundTag();
            subLevel.setUserDataTag(userData);
        }
        if (!userData.contains(FUSE_DATA)) userData.put(FUSE_DATA, new CompoundTag());
        return userData.getCompound(FUSE_DATA);
    }

    private static String positionKey(BlockPos position) {
        return Long.toString(position.asLong());
    }
}
