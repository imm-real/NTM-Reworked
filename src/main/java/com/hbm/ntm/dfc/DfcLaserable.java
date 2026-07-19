package com.hbm.ntm.dfc;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Source ILaserable boundary used by Dark Fusion Core SPK beams. */
public interface DfcLaserable {
    void addDfcEnergy(ServerLevel level, long energy, Direction incomingDirection);
}
