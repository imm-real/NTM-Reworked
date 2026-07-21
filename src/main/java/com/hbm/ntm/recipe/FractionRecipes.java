package com.hbm.ntm.recipe;

import com.hbm.ntm.registry.ModFluids;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.function.Supplier;

/** Registered normal-oil Fractioning Tower recipes. */
public final class FractionRecipes {
    private static final List<FractionRecipe> RECIPES = List.of(
            new FractionRecipe(ModFluids.HEAVYOIL, ModFluids.BITUMEN, 30, ModFluids.SMEAR, 70),
            new FractionRecipe(ModFluids.SMEAR, ModFluids.HEATINGOIL, 60, ModFluids.LUBRICANT, 40),
            new FractionRecipe(ModFluids.NAPHTHA, ModFluids.HEATINGOIL, 40, ModFluids.DIESEL, 60),
            new FractionRecipe(ModFluids.LIGHTOIL, ModFluids.DIESEL, 40, ModFluids.KEROSENE, 60)
    );

    private FractionRecipes() { }

    public static FractionRecipe get(Fluid input) {
        for (FractionRecipe recipe : RECIPES) {
            if (input.isSame(recipe.input().get())) return recipe;
        }
        return null;
    }

    public static int recipeCount() { return RECIPES.size(); }

    public static List<FractionRecipe> all() { return RECIPES; }

    public record FractionRecipe(Supplier<? extends Fluid> input,
                                 Supplier<? extends Fluid> outputLeft, int outputLeftAmount,
                                 Supplier<? extends Fluid> outputRight, int outputRightAmount) {
        public static final int INPUT_AMOUNT = 100;
    }
}
