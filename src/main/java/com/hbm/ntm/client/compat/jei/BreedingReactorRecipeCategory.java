package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.recipe.BreederRecipes.DisplayRecipe;
import com.hbm.ntm.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class BreedingReactorRecipeCategory implements IRecipeCategory<DisplayRecipe> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_breeder.png");

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawableAnimated progress;

    public BreedingReactorRecipeCategory(IGuiHelper gui) {
        background = gui.createDrawable(TEXTURE, 5, 11, 166, 65);
        icon = gui.createDrawableItemLike(ModItems.MACHINE_REACTOR_BREEDING_ITEM.get());
        progress = gui.createAnimatedDrawable(gui.createDrawable(TEXTURE, 176, 0, 70, 20),
                50, IDrawableAnimated.StartDirection.LEFT, false);
    }

    @Override
    public RecipeType<DisplayRecipe> getRecipeType() {
        return HbmJeiPlugin.BREEDING_REACTOR;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("container.reactorBreeding");
    }

    @Override
    public int getWidth() {
        return 166;
    }

    @Override
    public int getHeight() {
        return 65;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DisplayRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(30, 24).addItemStack(recipe.input());
        builder.addOutputSlot(120, 24).addItemStack(recipe.output());
    }

    @Override
    public void draw(DisplayRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        background.draw(graphics, 0, 0);
        progress.draw(graphics, 48, 21);
        String flux = Integer.toString(recipe.flux());
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, flux, 83 - font.width(flux) / 2, 10, 0x08FF00, false);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    @Override
    public ResourceLocation getRegistryName(DisplayRecipe recipe) {
        return recipe.id();
    }
}
