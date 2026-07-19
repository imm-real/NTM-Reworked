package com.hbm.ntm.compat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Optional coordinate-frame bridge for turbofans mounted on moving structures.
 * Implementations keep the base machine independent from the vehicle mod.
 */
public interface TurbofanAirflowFrame {
    Vec3 hbm$localVectorToWorld(Vec3 localVector);

    AABB hbm$worldBoundsToLocal(AABB worldBounds);

    double hbm$distanceSquaredToLocalPosition(Vec3 observerPosition, Vec3 localPosition);
}
