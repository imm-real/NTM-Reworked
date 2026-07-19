package com.hbm.ntm.hazard;

public enum HazardProtection {
    GAS_LUNG("hazard.hbm.gas_chlorine"),
    GAS_MONOXIDE("hazard.hbm.gas_monoxide"),
    GAS_INERT("hazard.hbm.gas_inert"),
    PARTICLE_COARSE("hazard.hbm.particle_coarse"),
    PARTICLE_FINE("hazard.hbm.particle_fine"),
    BACTERIA("hazard.hbm.bacteria"),
    GAS_BLISTERING("hazard.hbm.corrosive"),
    SAND("hazard.hbm.sand"),
    LIGHT("hazard.hbm.light");

    private final String translationKey;

    HazardProtection(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}
