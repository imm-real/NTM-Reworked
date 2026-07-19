package com.hbm.ntm.foundry;

import net.minecraft.core.Direction;

/** Shared molten-material endpoint used by crucibles, channels, tanks, outlets, and casting basins. */
public interface MoltenAcceptor {
    boolean canAcceptPour(FoundryMaterial material, int offered, Direction inputSide);

    int acceptPour(FoundryMaterial material, int offered, Direction inputSide);

    boolean canAcceptFlow(FoundryMaterial material, int offered, Direction inputSide);

    int acceptFlow(FoundryMaterial material, int offered, Direction inputSide);
}
