package com.hbm.ntm.energy;

import net.minecraft.core.Direction;

public interface HeConnector {
    default boolean canConnect(Direction side) {
        return side != null;
    }
}
