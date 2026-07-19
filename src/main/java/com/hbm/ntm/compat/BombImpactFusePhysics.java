package com.hbm.ntm.compat;

/** Sable-free impact-fuse policy shared by the optional Aeronautics integration and GameTests. */
public final class BombImpactFusePhysics {
    /** Matches Sable's own impact-prime threshold for vanilla TNT. */
    public static final double MIN_IMPACT_SPEED = 5.0D;
    /** Prevents deck bumps, rough landings and low releases from arming an HBM bomb. */
    public static final double MIN_DROP_HEIGHT = 8.0D;

    private BombImpactFusePhysics() { }

    public static boolean shouldDetonate(double impactSpeed, double releaseAltitude,
                                         double currentAltitude) {
        if (!Double.isFinite(impactSpeed) || !Double.isFinite(releaseAltitude)
                || !Double.isFinite(currentAltitude)) return false;
        return Math.abs(impactSpeed) >= MIN_IMPACT_SPEED
                && releaseAltitude - currentAltitude >= MIN_DROP_HEIGHT;
    }
}
