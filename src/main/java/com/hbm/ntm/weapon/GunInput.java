package com.hbm.ntm.weapon;

public enum GunInput {
    PRIMARY,
    RELOAD,
    TOGGLE_AIM,
    PRIMARY_RELEASE,
    SECONDARY,
    SECONDARY_RELEASE;

    public static GunInput byId(int id) {
        GunInput[] values = values();
        return id >= 0 && id < values.length ? values[id] : PRIMARY;
    }
}
