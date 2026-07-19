package com.hbm.ntm.recipe;

import com.hbm.ntm.registry.ModItems;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * Source-order solid Electric Arc Furnace recipes needed by the silicon progression.
 * Fiberglass and material-block variants can be added when those forms are registered.
 */
public final class ArcFurnaceRecipes {
    private static final List<Recipe> RECIPES = List.of(
            recipe(Ingredient.of(ItemTags.SAND), "nugget_silicon", 1),
            recipe(Ingredient.of(Items.FLINT), "nugget_silicon", 4),
            recipe(Ingredient.of(Items.QUARTZ), "nugget_silicon", 3),
            recipe(Ingredient.of(ModItems.get("powder_quartz").get()), "nugget_silicon", 3),
            recipe(Ingredient.of(Items.QUARTZ_BLOCK), "nugget_silicon", 12),
            recipe(Ingredient.of(ModItems.get("ingot_asbestos").get()), "nugget_silicon", 4),
            recipe(Ingredient.of(ModItems.get("powder_asbestos").get()), "nugget_silicon", 4)
    );

    private ArcFurnaceRecipes() { }

    public static List<Recipe> all() { return RECIPES; }

    public static Recipe find(ItemStack input) {
        if (input.isEmpty()) return null;
        for (Recipe recipe : RECIPES) if (recipe.input().test(input)) return recipe;
        return null;
    }

    private static Recipe recipe(Ingredient input, String output, int count) {
        return new Recipe(input, new ItemStack(ModItems.get(output).get(), count));
    }

    public record Recipe(Ingredient input, ItemStack output) {
        public Recipe {
            output = output.copy();
        }

        @Override public ItemStack output() { return output.copy(); }
    }
}
