package com.hbm.ntm.compat;

import net.minecraft.core.Direction;

/** Shared, Sable-free values used by the optional moving-vehicle integration. */
public final class TurbofanVehiclePhysics {
    /** Normal combustion output from one millibucket of aviation fuel. */
    public static final double BASE_OUTPUT = 3_850.0D;
    /** One normal turbofan is balanced to one max-speed Aeronautics small propeller. */
    public static final double BASE_THRUST = 256.0D;
    public static final double BASE_AIRFLOW = 25.6D;

    private TurbofanVehiclePhysics() { }

    /** The exhaust leaves opposite the machine's existing intake-to-exhaust airflow axis. */
    public static Direction exhaustDirection(Direction placementFacing) {
        return placementFacing.getClockWise().getOpposite();
    }

    /** Sable thrust in pN, tied to HE output so afterburners still mean business. */
    public static double thrust(int output) {
        return output > 0 ? BASE_THRUST * output / BASE_OUTPUT : 0.0D;
    }

    /** Sable airflow in m/s; the square-root curve raises terminal speed without exploding it. */
    public static double airflow(int output) {
        return output > 0 ? BASE_AIRFLOW * Math.sqrt(output / BASE_OUTPUT) : 0.0D;
    }

    public static boolean isActive(boolean wasOn, int output, int consumption) {
        return wasOn && output > 0 && consumption > 0;
    }
}
