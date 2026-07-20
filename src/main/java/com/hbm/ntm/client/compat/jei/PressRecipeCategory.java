package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.item.StampItem;
import com.hbm.ntm.recipe.PressRecipes;
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
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class PressRecipeCategory implements IRecipeCategory<PressRecipes.PressRecipe> {
    private final IDrawable icon;
    private final IDrawable arrow;

    public PressRecipeCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(ModItems.MACHINE_PRESS_ITEM.get());
        arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<PressRecipes.PressRecipe> getRecipeType() {
        return HbmJeiPlugin.PRESS;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.hbm.press");
    }

    @Override
    public int getWidth() {
        return 150;
    }

    @Override
    public int getHeight() {
        return 68;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PressRecipes.PressRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(35, 4)
                .setStandardSlotBackground()
                .addItemStacks(stampsFor(recipe.stampType()));
        builder.addInputSlot(35, 40)
                .setStandardSlotBackground()
                .addIngredients(recipe.input());
        builder.addOutputSlot(115, 22)
                .setOutputSlotBackground()
                .addItemStack(recipe.output().get().copy());
    }

    @Override
    public void draw(PressRecipes.PressRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, 76, 25);
    }

    private static List<ItemStack> stampsFor(StampItem.StampType type) {
        return ModItems.STAMPS.values().stream()
                .filter(holder -> holder.get() instanceof StampItem stamp && stamp.stampType() == type)
                .map(holder -> new ItemStack(holder.get()))
                .toList();
    }
}
