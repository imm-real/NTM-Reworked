package com.hbm.ntm.recipe;

import com.hbm.ntm.item.FluidIdentifierItem;

/** Flammable-fluid energy divided from buckets into TU per mB. */
public final class FluidBurnerFuels {
    private FluidBurnerFuels() { }

    public static int heatPerMb(FluidIdentifierItem.Selection fluid) {
        return switch (fluid) {
            case OIL -> 10;
            case HEAVYOIL, SMEAR -> 50;
            case HEATINGOIL -> 150;
            case WOODOIL -> 110;
            case COALCREOSOTE -> 250;
            case NAPHTHA -> 125;
            case LIGHTOIL, DIESEL -> 200;
            case KEROSENE -> 300;
            case GAS -> 10;
            case PETROLEUM -> 25;
            case FLUE -> 25;
            case HYDROGEN -> 5;
            case UNSATURATEDS -> 1_000;
            default -> 0;
        };
    }

    public static boolean flammable(FluidIdentifierItem.Selection fluid) {
        return heatPerMb(fluid) > 0;
    }

    public static boolean polluting(FluidIdentifierItem.Selection fluid) {
        return flammable(fluid) && fluid != FluidIdentifierItem.Selection.HYDROGEN;
    }
}
