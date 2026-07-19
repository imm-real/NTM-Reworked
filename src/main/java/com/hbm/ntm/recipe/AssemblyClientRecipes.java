package com.hbm.ntm.recipe;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AssemblyClientRecipes {
    private static volatile Map<ResourceLocation, AssemblyRecipe> recipes = Map.of();
    private AssemblyClientRecipes() { }
    public static void replace(List<AssemblyRecipe> values) {
        Map<ResourceLocation, AssemblyRecipe> ordered = new LinkedHashMap<>();
        for (AssemblyRecipe recipe : values) ordered.put(recipe.id(), recipe);
        recipes = Collections.unmodifiableMap(ordered);
    }
    public static AssemblyRecipe get(ResourceLocation id) { return recipes.get(id); }
    public static List<AssemblyRecipe> all() { return List.copyOf(recipes.values()); }
}
