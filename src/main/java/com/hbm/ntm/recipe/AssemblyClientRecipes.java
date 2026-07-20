package com.hbm.ntm.recipe;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class AssemblyClientRecipes {
    private static volatile Map<ResourceLocation, AssemblyRecipe> recipes = Map.of();
    private static volatile Consumer<List<AssemblyRecipe>> updateListener = ignored -> { };
    private AssemblyClientRecipes() { }
    public static void replace(List<AssemblyRecipe> values) {
        Map<ResourceLocation, AssemblyRecipe> ordered = new LinkedHashMap<>();
        for (AssemblyRecipe recipe : values) ordered.put(recipe.id(), recipe);
        recipes = Collections.unmodifiableMap(ordered);
        updateListener.accept(all());
    }
    public static AssemblyRecipe get(ResourceLocation id) { return recipes.get(id); }
    public static List<AssemblyRecipe> all() { return List.copyOf(recipes.values()); }

    public static void setUpdateListener(Consumer<List<AssemblyRecipe>> listener) {
        updateListener = listener == null ? ignored -> { } : listener;
    }
}
