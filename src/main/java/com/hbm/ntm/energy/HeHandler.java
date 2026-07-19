package com.hbm.ntm.energy;

public interface HeHandler extends HeConnector, HeLoaded {
    long getPower();

    void setPower(long power);

    long getMaxPower();
}
