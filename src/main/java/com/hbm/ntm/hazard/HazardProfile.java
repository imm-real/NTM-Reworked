package com.hbm.ntm.hazard;

/**
 * Immutable resolved hazard levels for one item form.
 * Levels are per item unless that hazard has been declared too special for stack size.
 */
public record HazardProfile(
        float radiation,
        float heat,
        float hydroactive,
        float explosive,
        float coalDust,
        float asbestos,
        float blinding,
        float digamma
) {
    public static final HazardProfile NONE = new HazardProfile(0, 0, 0, 0, 0, 0, 0, 0);

    public static HazardProfile radiation(float radiation) {
        return NONE.withRadiation(radiation);
    }

    public HazardProfile add(HazardProfile other) {
        return new HazardProfile(
                radiation + other.radiation,
                heat + other.heat,
                hydroactive + other.hydroactive,
                explosive + other.explosive,
                coalDust + other.coalDust,
                asbestos + other.asbestos,
                blinding + other.blinding,
                digamma + other.digamma
        );
    }

    public HazardProfile scale(float multiplier) {
        return new HazardProfile(
                radiation * multiplier,
                heat * multiplier,
                hydroactive * multiplier,
                explosive * multiplier,
                coalDust * multiplier,
                asbestos * multiplier,
                blinding * multiplier,
                digamma * multiplier
        );
    }

    public HazardProfile withRadiation(float value) {
        return new HazardProfile(value, heat, hydroactive, explosive, coalDust, asbestos, blinding, digamma);
    }

    public HazardProfile withHeat(float value) {
        return new HazardProfile(radiation, value, hydroactive, explosive, coalDust, asbestos, blinding, digamma);
    }

    public HazardProfile withHydroactive(float value) {
        return new HazardProfile(radiation, heat, value, explosive, coalDust, asbestos, blinding, digamma);
    }

    public HazardProfile withExplosive(float value) {
        return new HazardProfile(radiation, heat, hydroactive, value, coalDust, asbestos, blinding, digamma);
    }

    public HazardProfile withCoalDust(float value) {
        return new HazardProfile(radiation, heat, hydroactive, explosive, value, asbestos, blinding, digamma);
    }

    public HazardProfile withAsbestos(float value) {
        return new HazardProfile(radiation, heat, hydroactive, explosive, coalDust, value, blinding, digamma);
    }

    public HazardProfile withBlinding(float value) {
        return new HazardProfile(radiation, heat, hydroactive, explosive, coalDust, asbestos, value, digamma);
    }

    public HazardProfile withDigamma(float value) {
        return new HazardProfile(radiation, heat, hydroactive, explosive, coalDust, asbestos, blinding, value);
    }

    public boolean isEmpty() {
        return radiation <= 0 && heat <= 0 && hydroactive <= 0 && explosive <= 0
                && coalDust <= 0 && asbestos <= 0 && blinding <= 0 && digamma <= 0;
    }
}
