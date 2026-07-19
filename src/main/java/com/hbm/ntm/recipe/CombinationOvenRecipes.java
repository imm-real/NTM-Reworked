package com.hbm.ntm.recipe;

import com.hbm.ntm.item.AshItem;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/** Combination oven recipes. Missing materials do not get stunt doubles. */
public final class CombinationOvenRecipes {
    private static final List<Recipe> RECIPES = List.of(
            itemRecipe("combination.coal", tag("c:gems/coal"), ModItems.COKE_COAL,
                    ModFluids.COALCREOSOTE, 100),
            itemRecipe("combination.coalDust", tag("c:dusts/coal"), ModItems.COKE_COAL,
                    ModFluids.COALCREOSOTE, 100),
            itemRecipe("combination.lignite", tag("c:gems/lignite"),
                    ModItems.COKE_LIGNITE, ModFluids.COALCREOSOTE, 50),
            itemRecipe("combination.ligniteDust", Ingredient.of(ModItems.get("powder_lignite").get()),
                    ModItems.COKE_LIGNITE, ModFluids.COALCREOSOTE, 50),
            stackRecipe("combination.cinnabar", tag("c:gems/cinnabar"),
                    () -> new ItemStack(ModItems.get("sulfur").get()), ModFluids.MERCURY, 100),
            stackRecipe("combination.log", Ingredient.of(ItemTags.LOGS),
                    () -> new ItemStack(Items.CHARCOAL), ModFluids.WOODOIL, 250),
            stackRecipe("combination.sapling", Ingredient.of(ItemTags.SAPLINGS),
                    () -> AshItem.create(ModItems.POWDER_ASH.get(), AshItem.AshType.WOOD),
                    ModFluids.WOODOIL, 50),
            itemRecipe("combination.tar", Ingredient.of(ModItems.OIL_TAR.get()),
                    ModItems.COKE_PETROLEUM, null, 0),
            stackRecipe("combination.clay", Ingredient.of(Blocks.CLAY),
                    () -> new ItemStack(Blocks.BRICKS), null, 0)
    );

    private CombinationOvenRecipes() { }

    public static List<Recipe> all() { return RECIPES; }

    @Nullable
    public static Recipe find(ItemStack input) {
        if (input.isEmpty()) return null;
        for (Recipe recipe : RECIPES) if (recipe.input().test(input)) return recipe;
        return null;
    }

    public static boolean isValidInput(ItemStack input) { return find(input) != null; }

    private static Recipe itemRecipe(String name, Ingredient input,
                                     Supplier<? extends net.minecraft.world.item.Item> output,
                                     @Nullable Supplier<? extends Fluid> fluid, int fluidAmount) {
        return stackRecipe(name, input, () -> new ItemStack(output.get()), fluid, fluidAmount);
    }

    private static Recipe stackRecipe(String name, Ingredient input, Supplier<ItemStack> output,
                                      @Nullable Supplier<? extends Fluid> fluid, int fluidAmount) {
        return new Recipe(name, input, output, fluid, fluidAmount);
    }

    private static Ingredient tag(String id) {
        return Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse(id)));
    }

    public record Recipe(String name, Ingredient input, Supplier<ItemStack> output,
                         @Nullable Supplier<? extends Fluid> fluid, int fluidAmount) {
        public ItemStack result() { return output.get().copy(); }
        @Nullable public Fluid resultFluid() { return fluid == null ? null : fluid.get(); }
    }
}
