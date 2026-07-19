package com.hbm.ntm.recipe;

import com.hbm.ntm.item.FluidDuctItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Stamps one or eight ducts with the selected fluid identity. */
public final class FluidDuctTypingRecipe extends CustomRecipe {
    public FluidDuctTypingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override public boolean matches(CraftingInput input, Level level) {
        return inspect(input) != null;
    }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Match match = inspect(input);
        return match == null ? ItemStack.EMPTY
                : FluidDuctItem.create(ModItems.FLUID_DUCT.get(), match.selection(), match.count());
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return ModRecipeSerializers.FLUID_DUCT_TYPING.get(); }

    private Match inspect(CraftingInput input) {
        FluidIdentifierItem.Selection selection = FluidIdentifierItem.Selection.NONE;
        int identifierCount = 0;
        int ductCount = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (stack.is(ModItems.FLUID_IDENTIFIER_MULTI.get())) {
                identifierCount++;
                selection = FluidIdentifierItem.primary(stack);
            } else if (stack.is(ModItems.FLUID_DUCT_NEO_ITEM.get()) || stack.is(ModItems.FLUID_DUCT.get())) {
                ductCount++;
            } else {
                return null;
            }
        }
        return identifierCount == 1 && selection != FluidIdentifierItem.Selection.NONE
                && (ductCount == 1 || ductCount == 8) ? new Match(selection, ductCount) : null;
    }

    private record Match(FluidIdentifierItem.Selection selection, int count) { }
}
