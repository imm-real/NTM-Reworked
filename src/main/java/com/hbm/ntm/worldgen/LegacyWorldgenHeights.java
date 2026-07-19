package com.hbm.ntm.worldgen;

import net.minecraft.world.level.LevelHeightAccessor;

/** Translates old absolute Y values into distance above the dimension floor. */
public final class LegacyWorldgenHeights {
    private LegacyWorldgenHeights() {
    }

    public static int aboveBottom(LevelHeightAccessor level, int legacyY) {
        return aboveBottom(level.getMinBuildHeight(), legacyY);
    }

    public static int aboveBottom(int minBuildHeight, int legacyY) {
        return minBuildHeight + legacyY;
    }
}
