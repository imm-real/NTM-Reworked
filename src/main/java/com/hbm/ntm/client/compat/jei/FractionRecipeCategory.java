package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.recipe.FractionRecipes.FractionRecipe;
import com.hbm.ntm.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class FractionRecipeCategory implements IRecipeCategory<FractionRecipe> {
    private final IDrawable icon;
    private final IDrawable arrow;

    public FractionRecipeCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(ModItems.MACHINE_FRACTION_TOWER_ITEM.get());
        arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<FractionRecipe> getRecipeType() {
        return HbmJeiPlugin.FRACTION;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.hbm.fraction");
    }

    @Override
    public int getWidth() {
        return 116;
    }

    @Override
    public int getHeight() {
        return 30;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FractionRecipe recipe, IFocusGroup focuses) {
        JeiFluidHelper.fluidSlot(builder, recipe.input().get(), FractionRecipe.INPUT_AMOUNT, true, 8, 6);
        JeiFluidHelper.fluidSlot(builder, recipe.outputLeft().get(), recipe.outputLeftAmount(), false, 68, 6);
        JeiFluidHelper.fluidSlot(builder, recipe.outputRight().get(), recipe.outputRightAmount(), false, 90, 6);
    }

    @Override
    public void draw(FractionRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, 36, 6);
    }
}
