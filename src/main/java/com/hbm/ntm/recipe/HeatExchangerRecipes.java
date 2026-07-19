package com.hbm.ntm.recipe;

import com.hbm.ntm.item.FluidIdentifierItem;

/** Registered FT_Coolable entries for the Heat Exchanging Heater. */
public final class HeatExchangerRecipes {
    private HeatExchangerRecipes() { }

    public record Cooling(FluidIdentifierItem.Selection input, int inputAmount,
                          FluidIdentifierItem.Selection output, int outputAmount, int heatPerOperation) { }

    public static Cooling get(FluidIdentifierItem.Selection input) {
        return switch (input) {
            case STEAM -> new Cooling(input, 100, FluidIdentifierItem.Selection.SPENTSTEAM, 1, 100);
            case HOTSTEAM -> new Cooling(input, 1, FluidIdentifierItem.Selection.STEAM, 10, 1);
            case SUPERHOTSTEAM -> new Cooling(input, 1, FluidIdentifierItem.Selection.HOTSTEAM, 10, 9);
            case ULTRAHOTSTEAM -> new Cooling(input, 1, FluidIdentifierItem.Selection.SUPERHOTSTEAM, 10, 60);
            case HOTOIL -> new Cooling(input, 1, FluidIdentifierItem.Selection.OIL, 1, 10);
            case COOLANT_HOT -> new Cooling(input, 1, FluidIdentifierItem.Selection.COOLANT, 1, 300);
            default -> null;
        };
    }
}
