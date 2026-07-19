package com.hbm.ntm.thermal;

public interface HeatSource {
    int getHeatStored();

    void useUpHeat(int heat);
}
