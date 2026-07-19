package com.hbm.ntm.conveyor;

/** The four conveyor families that escaped into the creative inventory. */
public enum ConveyorType {
    REGULAR("regular", 1.0D, 1),
    EXPRESS("express", 3.0D, 1),
    DOUBLE("double", 1.0D, 2),
    TRIPLE("triple", 1.0D, 3);

    private final String id;
    private final double speedMultiplier;
    private final int lanes;

    ConveyorType(String id, double speedMultiplier, int lanes) {
        this.id = id;
        this.speedMultiplier = speedMultiplier;
        this.lanes = lanes;
    }

    public String id() {
        return id;
    }

    public double speedMultiplier() {
        return speedMultiplier;
    }

    public int lanes() {
        return lanes;
    }

    public boolean supportsVerticalRouting() {
        return this == REGULAR;
    }
}
