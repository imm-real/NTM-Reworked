package com.hbm.ntm.recipe;

import com.hbm.ntm.registry.ModFluids;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.function.Supplier;

/** Registered Catalytic Cracking Tower recipes. */
public final class CrackingRecipes {
    private static final List<CrackingRecipe> RECIPES = List.of(
            new CrackingRecipe(ModFluids.GAS, 100, ModFluids.PETROLEUM, 30,
                    ModFluids.UNSATURATEDS, 20)
    );

    private CrackingRecipes() { }

    public static CrackingRecipe get(Fluid input) {
        for (CrackingRecipe recipe : RECIPES) {
            if (input.isSame(recipe.input().get())) return recipe;
        }
        return null;
    }

    public static int recipeCount() { return RECIPES.size(); }

    public static List<CrackingRecipe> all() { return RECIPES; }

    public record CrackingRecipe(Supplier<? extends Fluid> input, int inputAmount,
                                 Supplier<? extends Fluid> outputLeft, int outputLeftAmount,
                                 Supplier<? extends Fluid> outputRight, int outputRightAmount) { }
}
