package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record AnvilJeiRecipe(ResourceLocation id, int tierLower, int tierUpper,
                            List<Input> inputs, List<AnvilRecipes.Output> outputs) {
    public record Input(AnvilRecipes.Input source, int count) {
        public List<ItemStack> displayStacks() {
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemStack candidate : source.ingredient().getItems()) {
                if (!source.matches(candidate)) continue;
                stacks.add(candidate.copyWithCount(count));
            }
            if (stacks.isEmpty()) stacks.add(source.display().get().copyWithCount(count));
            return stacks;
        }
    }

    public static List<AnvilJeiRecipe> all() {
        List<AnvilJeiRecipe> recipes = new ArrayList<>();
        for (AnvilRecipes.Construction recipe : AnvilRecipes.construction()) {
            recipes.add(new AnvilJeiRecipe(recipe.id(), recipe.tierLower(), recipe.tierUpper(),
                    recipe.inputs().stream().map(input -> new Input(input, input.count())).toList(),
                    recipe.outputs()));
        }

        int index = 0;
        for (AnvilRecipes.Smithing recipe : AnvilRecipes.smithing()) {
            recipes.add(new AnvilJeiRecipe(
                    ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "jei/anvil/smithing_" + index++),
                    recipe.tier(), recipe.tier(),
                    List.of(new Input(recipe.left(), recipe.leftConsumed()),
                            new Input(recipe.right(), recipe.rightConsumed())),
                    List.of(new AnvilRecipes.Output(recipe.output(), 1.0F))));
        }
        return List.copyOf(recipes);
    }
}
