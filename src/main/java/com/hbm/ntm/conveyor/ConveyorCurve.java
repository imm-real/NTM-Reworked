package com.hbm.ntm.conveyor;

import net.minecraft.util.StringRepresentable;

public enum ConveyorCurve implements StringRepresentable {
    STRAIGHT("straight"),
    LEFT("left"),
    RIGHT("right");

    private final String id;

    ConveyorCurve(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public ConveyorCurve next() {
        return switch (this) {
            case STRAIGHT -> LEFT;
            case LEFT -> RIGHT;
            case RIGHT -> STRAIGHT;
        };
    }
}
